package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.OpCode;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RData;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import io.gitlab.teamproject2026.model.rdata.RDataAAAA;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.resolver.SListWrapper.*;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;

import java.lang.invoke.SerializedLambda;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.HashSet;

/*
 * Iterative resolver loop (equivalent to RFC 1034 5.3.3).
 *
 * Flow: seed SLIST -> pick server -> send query -> next server on no response
 *       -> SERVFAIL when SLIST is empty.
 *
 * Notes:
 * - resolve(name, ...) is recursive on purpose. CNAME restart and glue-less
 *   lookups (Step 2/3) just call it again with a different name.
 */
public class RecursiveResolver extends AbstractResolver{

    private final CacheHandler cache;

    /** Depth limit so NS/glue resolution doesn't loop infinitely. */
    private static final int MAX_DEPTH = 16;

    public RecursiveResolver(CacheHandler cache, int timeoutMs, long retryDelay) {
        this.cache = cache;
        this.timeoutMs=timeoutMs;
        this.retryDelay=retryDelay;
    }

    @Override
        public byte[] resolve(byte[] query){
            DNSPacket queryPacket;
            queryPacket = DNSPacket.fromBytes(query);
            //clear RA, RD bit
            queryPacket.setRa(false);
            queryPacket.setRd(false);

            QuestionSection question= queryPacket.getQuestionSections()[0];
            // resolve one question.
            return resolve(question.getName(), question.getQtype(), queryPacket.toBytes(), ResolveContext.start());
        }




    // Re-entrant core. Resolves sname/qtype from a fresh SLIST.
    // Terminates because the SLIST strictly shrinks: 
    // - each pass either returns or removes a server.
    // Step 2 keeps this property:
    // - referrals prune higher-level
    // servers (so delegations only descend), and CNAME loops get a visited-set.
    private byte[] resolve(String[] sname, RRType qtype, byte[] query, ResolveContext ctx){

        // Cap glue/CNAME resolution to avoid infinite recursion.
        if (ctx.depth() > MAX_DEPTH) {
            return buildServFail(query);
        }

        // fill slist
        SListWrapper slistWrap;
        try {
            slistWrap = new SListWrapper(sname, cache);
        } catch (SQLException e) {
            slistWrap = new SListWrapper(sname);
        }

        while (true) {

            //  find the best server
            SListEntry bestNsserver = slistWrap.getBestServer();
            // SLIST empty.
            if (bestNsserver == null ||
                    (bestNsserver.ipv4().toString().equals("0.0.0.0") && bestNsserver.ipv6().toString().equals("0000:0000:0000:0000:0000:0000:0000:0000"))
                    ||bestNsserver.nsName().getNsdname().length == 0){
                return buildServFail(query);
            }

            //build NameServer for queryUDP/queryTCP
            String ipv4Str = bestNsserver.ipv4().toString();
            String ipv6Str = bestNsserver.ipv6().toString();
            String nsName =  bestNsserver.nsName().toString();

            NameServer queriedServerIpv4 = new NameServer(ipv4Str, 53, nsName);
            NameServer queriedServerIpv6 = new NameServer(ipv6Str, 53, nsName);

            // send iterative query
            QueryResult result = null;
            NameServer currentServer = null;
            //query UDP: try ipv4, if not available use ipv6
            if (!queriedServerIpv4.host().equals("0.0.0.0")) {
                currentServer = queriedServerIpv4;
                logger.logTrace(PINK + "[TRACE] Sending query: " + String.join(".",sname) + ". to " + currentServer.name()+ RESET);
                result = queryUDP(query, queriedServerIpv4, timeoutMs);
            } else if (!queriedServerIpv6.host().equals("0000:0000:0000:0000:0000:0000:0000:0000")){
                currentServer = queriedServerIpv6;
                logger.logTrace(PINK + "[TRACE] Sending query: " + String.join(".",sname) + ". to " + currentServer.name()+ RESET);
                result = queryUDP(query, queriedServerIpv6, timeoutMs);
            }

            //handle failures: new while iteration with new server
            if (result==null||result.status()==Status.TIMEOUT||result.status()==Status.IO_ERROR) {
                sleep(retryDelay);
                logger.logTrace(PINK + "[TRACE] Query failure, retry"+ RESET);
                continue;
            }

            //fallback to TCP in case of truncation
            if (isTruncated(result.response())) {
                logger.logTrace(PINK + "[TRACE] Query truncated, retrying over TCP"+ RESET);
                QueryResult tcpResult = queryTCP(query, currentServer, timeoutMs);
                //handle failures: new while iteration with new server
                if (tcpResult == null || tcpResult.status() != Status.SUCCESS) {
                    sleep(retryDelay);
                    continue;
                }
                result = tcpResult;
            }

            DNSPacket response = DNSPacket.fromBytes(result.response());

            // Insert everything this hop returned
            try {
                cache.insertAll(response.getAnswerSections());
                cache.insertAll(response.getAuthoritySections());
                cache.insertAll(response.getAdditionalSections());
            } catch (SQLException e) {
                // caching is best-effort: keep resolving even if the insert fails
                logger.log(
                        (RED + "[RECURSIVE] cache insert failed: " + e.getMessage() + RESET),
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.ERROR
                );
                //IO.println(RED + "[RECURSIVE] cache insert failed: " + e.getMessage() + RESET);
            }

            // which branch of the RCODE decision tree is this?
            ReferralAnalyzer.Classification verdict = ReferralAnalyzer.classify(response, qtype);

            switch (verdict) {
                // The response is the answer, a final SOA, or a DNS error: return it.
                case ANSWER, NODATA, NXDOMAIN, FORMERR -> {
                    return response.toBytes();
                }
                // This server could not answer: try the next one.
                case SERVFAIL -> {
                    logger.logTrace(PINK + "[TRACE] Server returned " + verdict.SERVFAIL + ", retry"+ RESET);
                    continue;
                }
                // Delegation: resolve any missing glue, add the servers, keep walking down.
                case REFERRAL -> {
                    for (Delegation d : extractDelegations(response)) {
                        logger.logTrace(PINK + "[TRACE] Recieved Referral for: " + String.join(".",sname)+". from: " + currentServer.name() +" to: " + String.join(".",((RDataNS)d.ns.getRdata()).getNsdname()) + "."+ RESET);
                        if (!d.hasGlue()) {
                            logger.logTrace(PINK + "[TRACE] No Address provided, resolving: " + String.join(".",((RDataNS)d.ns.getRdata()).getNsdname()) + "." + RESET);
                            d = resolveNsAddress(d, ctx);
                            logger.logTrace(PINK + "[TRACE] finished resolving: " + String.join(".",((RDataNS)d.ns.getRdata()).getNsdname()) + " continuing to resolve: " + String.join(".",sname) + "."+ RESET);
                        }
                        slistWrap.addSListEntry(d.ns(), d.ipv4(), d.ipv6());
                    }
                    continue;
                }
                // Alias: restart at the canonical name, then prepend the CNAME we followed.
                case CNAME_CHAIN -> {
                    String[] target = cnameTarget(response);
                    if (target == null) {
                        return buildServFail(query);
                    }
                    // loop guard: a target we have already followed means a CNAME loop
                    logger.logTrace(PINK + "[TRACE] Recieved CNAME for: " + String.join(".", sname) + ". to: " + String.join(".",target) + "."+ RESET);
                    if (!ctx.cnamesSeen().add(String.join(".", target))) {
                        return buildServFail(query);
                    }
                    // resolve the target via cache (following cached CNAMEs) then iterate the tail
                    return prependCnames(response, resolveCnameTarget(target, qtype, ctx)).toBytes();
                }
            }

        }
    }


    /** Per-resolution state: recursion depth (capped) and the CNAME loop guard. */
    record ResolveContext(int depth, Set<String> cnamesSeen) {
        static ResolveContext start() {
            return new ResolveContext(0, new HashSet<>());
        }
        ResolveContext deeper() {
            return new ResolveContext(depth + 1, cnamesSeen);
        }
    }


    /**
     * Resolves a glue-less delegation's name server address (cache first, then iterative).
     *
     * @param d the glue-less delegation
     * @param ctx the resolution context
     * @return the delegation with its address, or unchanged if it cannot be resolved
     */
    Delegation resolveNsAddress(Delegation d, ResolveContext ctx) {
        String[] nsName = ((RDataNS) d.ns().getRdata()).getNsdname();

        // prefer IPv4, fall back to IPv6 if the name server is v6-only
        RData a = resolveAddress(nsName, RRType.A, ctx);
        if (a != null) {
            return new Delegation(d.ns(), (RDataA) a, d.ipv6());
        }
        RData aaaa = resolveAddress(nsName, RRType.AAAA, ctx);
        if (aaaa != null) {
            return new Delegation(d.ns(), d.ipv4(), (RDataAAAA) aaaa);
        }
        return d;
    }

    /**
     * Looks up an address record for a name, cache first then iterative.
     *
     * @param name the name server's host name
     * @param type the address type (A or AAAA)
     * @param ctx the resolution context
     * @return the matching address rdata, or null if it cannot be resolved
     */
    RData resolveAddress(String[] name, RRType type, ResolveContext ctx) {
        try {
            ResourceRecord[] cached = cache.lookup(String.join(".", name), type);
            if (cached.length > 0) {
                return cached[0].getRdata();
            }
        } catch (SQLException e) {
            // cache unavailable: fall through to an iterative lookup
            logger.log(
                    (RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO
            );
            //IO.println(RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage() + RESET);
        }
        byte[] response = resolve(name, type, buildQuery(name, type), ctx.deeper());
        DNSPacket parsed = DNSPacket.fromBytes(response);
        if (parsed.getAnswerSections() != null) {
            for (ResourceRecord rr : parsed.getAnswerSections()) {
                if (rr.getType() == type) {
                    return rr.getRdata();
                }
            }
        }
        return null;
    }


    /**
     * The canonical name a CNAME response points to.
     *
     * @param response a response classified as a CNAME chain
     * @return the canonical name as labels, or null if there is no CNAME
     */
    static String[] cnameTarget(DNSPacket response) {
        for (ResourceRecord rr : response.getAnswerSections()) {
            if (rr.getType() == RRType.CNAME) {
                return ((RDataCNAME) rr.getRdata()).getCname();
            }
        }
        return null;
    }

    /**
     * Prepends the followed CNAME records onto a resolved answer, building the chain.
     *
     * @param cnameResponse the response holding the CNAME(s) we followed
     * @param resolved the response for the canonical name
     * @return the merged response, CNAME records first then the resolved answer
     */
    static DNSPacket prependCnames(DNSPacket cnameResponse, DNSPacket resolved) {
        List<ResourceRecord> answer = new ArrayList<>();
        for (ResourceRecord rr : cnameResponse.getAnswerSections()) {
            if (rr.getType() == RRType.CNAME) {
                answer.add(rr);
            }
        }
        for (ResourceRecord rr : resolved.getAnswerSections()) {
            answer.add(rr);
        }
        resolved.setAnswerSections(answer.toArray(new ResourceRecord[0]));
        return resolved;
    }

    /**
     * Resolves a name using the cache as far as it reaches: it follows cached CNAME
     * records and only resolves the remaining tail of the chain iteratively.
     *
     * @param name the name to resolve
     * @param qtype the wanted record type
     * @param ctx the resolution context
     * @return a response, cached CNAMEs prepended onto the resolved tail
     */
    DNSPacket resolveCnameTarget(String[] name, RRType qtype, ResolveContext ctx) {
        // the wanted type is cached directly
        DNSPacket direct = cachedResponse(name, qtype);
        if (direct != null) {
            return direct;
        }
        // the name is a cached alias: follow the cached CNAME, then resolve the rest
        if (qtype != RRType.CNAME) {
            DNSPacket cnamePacket = cachedResponse(name, RRType.CNAME);
            if (cnamePacket != null) {
                String[] next = cnameTarget(cnamePacket);
                if (next != null && ctx.cnamesSeen().add(String.join(".", next))) {
                    return prependCnames(cnamePacket, resolveCnameTarget(next, qtype, ctx));
                }
            }
        }
        // end of the cached chain: resolve the tail over the network
        return DNSPacket.fromBytes(resolve(name, qtype, buildQuery(name, qtype), ctx.deeper()));
    }

    /**
     * A response built from cached records for the name and type, or null on a cache miss.
     *
     * @param name the name to look up
     * @param qtype the record type
     * @return a NOERROR response with the cached records, or null if not cached
     */
    DNSPacket cachedResponse(String[] name, RRType qtype) {
        ResourceRecord[] cached;
        try {
            cached = cache.lookup(String.join(".", name), qtype);
        } catch (SQLException e) {
            // treat a cache error like a miss so the caller falls back to iterating
            logger.log(
                    (RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO
            );
            //IO.println(RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage() + RESET);
            return null;
        }
        if (cached.length == 0) {
            return null;
        }
        QuestionSection question = new QuestionSection();
        question.setName(name);
        question.setQtype(qtype);
        question.setQclass(RRClass.IN);

        DNSPacket packet = new DNSPacket();
        packet.setId(randomId());
        packet.setOpcode(OpCode.QUERY);
        packet.setRcode(RCode.NOERROR);
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(cached);
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);
        return packet;
    }

    /**
     * Builds a minimal iterative query (RD cleared) for one question.
     *
     * @param name the query name
     * @param qtype the query type
     * @return the query bytes
     */
    private static byte[] buildQuery(String[] name, RRType qtype) {
        QuestionSection question = new QuestionSection();
        question.setName(name);
        question.setQtype(qtype);
        question.setQclass(RRClass.IN);

        DNSPacket packet = new DNSPacket();
        packet.setId(randomId());
        packet.setOpcode(OpCode.QUERY);
        packet.setQr(false);
        packet.setRd(false);
        packet.setRcode(RCode.NOERROR);
        packet.setQuestionSections(new QuestionSection[]{question});
        packet.setAnswerSections(new ResourceRecord[0]);
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);
        return packet.toBytes();
    }

    /** @return a random 16-bit DNS transaction id */
    private static byte[] randomId() {
        byte[] id = new byte[2];
        ThreadLocalRandom.current().nextBytes(id);
        return id;
    }


    // Glue extraction: pull the NS records and their glue out of a referral response.

    // One delegation from a referral: NS record + glue addresses.
    // No glue = zero address (byte[4]/byte[16]) -> resolve the NS first.
    record Delegation(ResourceRecord ns, RDataA ipv4, RDataAAAA ipv6) {
        boolean hasGlue() {
            return !isZero(ipv4.getAddress()) || !isZero(ipv6.getAddress());
        }
    }

    /**
     * Pulls the NS records from the authority section and matches their glue
     * (A / AAAA) from the additional section. One Delegation per NS record.
     *
     * @param response - a referral response from a name server
     * @return one Delegation per NS record, with zero-address glue where none was provided
     */
    static List<Delegation> extractDelegations(DNSPacket response) {
        List<Delegation> delegations = new ArrayList<>();
        ResourceRecord[] authority = response.getAuthoritySections();
        ResourceRecord[] additional = response.getAdditionalSections();
        if (authority == null) {
            return delegations;
        }
        for (ResourceRecord rr : authority) {
            if (rr.getType() != RRType.NS) {
                continue;
            }
            String nsHost = String.join(".", ((RDataNS) rr.getRdata()).getNsdname());
            RData a = findGlue(additional, nsHost, RRType.A);
            RData  aaaa = findGlue(additional, nsHost, RRType.AAAA);
            RDataA ipv4 = a != null ? (RDataA) a : new RDataA(new byte[4]);
            RDataAAAA ipv6 = aaaa != null ? (RDataAAAA) aaaa : new RDataAAAA(new byte[16]);
            delegations.add(new Delegation(rr, ipv4, ipv6));
        }
        return delegations;
    }

    /**
     * Finds the glue rdata of a given type for a name server in the additional section.
     *
     * @param additional the additional section of a response (may be null)
     * @param nsHost     the name server's host name
     * @param type       the record type to look for (A or AAAA)
     * @return the matching rdata, or null if none is present
     */
    private static RData findGlue(ResourceRecord[] additional, String nsHost, RRType type) {
        if (additional == null) {
            return null;
        }
        for (ResourceRecord rr : additional) {
            if (rr.getType() == type && String.join(".", rr.getName()).equalsIgnoreCase(nsHost)) {
                return rr.getRdata();
            }
        }
        return null;
    }

    /**
     * Whether an address is all zeroes, the placeholder for missing glue.
     *
     * @param address the address bytes (may be null)
     * @return true if the address is null or all zero
     */
    private static boolean isZero(byte[] address) {
        if (address == null) {
            return true;
        }
        for (byte b : address) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

}
