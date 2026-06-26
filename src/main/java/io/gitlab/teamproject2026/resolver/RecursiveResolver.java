package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
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
                result = queryUDP(query, queriedServerIpv4, timeoutMs);
            } else if (!queriedServerIpv6.host().equals("0000:0000:0000:0000:0000:0000:0000:0000")){
                currentServer = queriedServerIpv6;
                result = queryUDP(query, queriedServerIpv6, timeoutMs);
            }

            //handle failures: new while iteration with new server
            if (result==null||result.status()==Status.TIMEOUT||result.status()==Status.IO_ERROR) {
                sleep(retryDelay);
                continue;
            }

            //fallback to TCP in case of truncation
            if (isTruncated(result.response())) {
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
                //keep resolving
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
                    continue;
                }
                // Delegation: resolve any missing glue, add the servers, keep walking down.
                case REFERRAL -> {
                    for (Delegation d : extractDelegations(response)) {
                        if (!d.hasGlue()) {
                            d = resolveNsAddress(d, ctx);
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
                    if (!ctx.cnamesSeen().add(String.join(".", target))) {
                        return buildServFail(query);
                    }
                    byte[] resolved = resolve(target, qtype, buildQuery(target, qtype), ctx.deeper());
                    return prependCnames(response, resolved);
                }
            }

        }
    }


    /** Per-resolution state: recursion depth (capped) and the CNAME loop guard. */
    private record ResolveContext(int depth, Set<String> cnamesSeen) {
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
    private Delegation resolveNsAddress(Delegation d, ResolveContext ctx) {
        String[] nsName = ((RDataNS) d.ns().getRdata()).getNsdname();

        // cache first
        try {
            ResourceRecord[] cached = cache.lookup(String.join(".", nsName), RRType.A);
            if (cached.length > 0) {
                return new Delegation(d.ns(), (RDataA) cached[0].getRdata(), d.ipv6());
            }
        } catch (SQLException ignored) {
        }

        // not cached: resolve iteratively
        byte[] response = resolve(nsName, RRType.A, buildQuery(nsName, RRType.A), ctx.deeper());
        DNSPacket parsed = DNSPacket.fromBytes(response);
        if (parsed.getAnswerSections() != null) {
            for (ResourceRecord rr : parsed.getAnswerSections()) {
                if (rr.getType() == RRType.A) {
                    return new Delegation(d.ns(), (RDataA) rr.getRdata(), d.ipv6());
                }
            }
        }
        return d;
    }


    /**
     * The canonical name a CNAME response points to.
     *
     * @param response a response classified as a CNAME chain
     * @return the canonical name as labels, or null if there is no CNAME
     */
    private static String[] cnameTarget(DNSPacket response) {
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
     * @param resolved the serialized response for the canonical name
     * @return the merged response bytes, CNAME records first then the resolved answer
     */
    private static byte[] prependCnames(DNSPacket cnameResponse, byte[] resolved) {
        DNSPacket result = DNSPacket.fromBytes(resolved);
        List<ResourceRecord> answer = new ArrayList<>();
        for (ResourceRecord rr : cnameResponse.getAnswerSections()) {
            if (rr.getType() == RRType.CNAME) {
                answer.add(rr);
            }
        }
        for (ResourceRecord rr : result.getAnswerSections()) {
            answer.add(rr);
        }
        result.setAnswerSections(answer.toArray(new ResourceRecord[0]));
        return result.toBytes();
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

    // Builds a minimal response carrying just an rcode (skeleton terminal).
    private DNSPacket placeholderRespond(RCode rcode) {
        DNSPacket response = new DNSPacket();
        response.setRcode(rcode);
        response.setOpcode(OpCode.QUERY);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        return response;
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
