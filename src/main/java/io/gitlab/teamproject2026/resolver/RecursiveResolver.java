package io.gitlab.teamproject2026.resolver;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.resolver.SListWrapper.SListEntry;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
public class RecursiveResolver extends AbstractResolver {

    private final CacheHandler cache;
    private final int ednsUdpSizeLimit;

    /** Depth limit so NS/glue resolution doesn't loop infinitely. */
    private static final int MAX_DEPTH = 16;

    private static final int MAX_NS_ADDRESS_DEPTH = 3;

    public RecursiveResolver(CacheHandler cache, Logger logger, int timeoutMs, long retryDelay, int ednsUdpSizeLimit) {
        this.cache = cache;
        this.logger = logger;
        this.timeoutMs = timeoutMs;
        this.retryDelay = retryDelay;
        this.ednsUdpSizeLimit = ednsUdpSizeLimit;
    }

    @Override
    public byte[] resolve(byte[] query) {
        DNSPacket queryPacket;
        queryPacket = DNSPacket.fromBytes(query);
        // clear RA, RD bit
        queryPacket.setRa(false);
        queryPacket.setRd(false);
        DNSPacket finalResponse = new DNSPacket();
        finalResponse.setId(queryPacket.getId());
        finalResponse.setQr(true);
        finalResponse.setOpcode(OpCode.QUERY);
        finalResponse.setRd(false);
        finalResponse.setRa(false);
        finalResponse.setQdcount(queryPacket.getQdcount());
        finalResponse.setAnswerSections(new ResourceRecord[0]);
        finalResponse.setAuthoritySections(new ResourceRecord[0]);
        finalResponse.setAdditionalSections(new ResourceRecord[0]);
        QuestionSection[] questions = queryPacket.getQuestionSections();
        finalResponse.setQuestionSections(queryPacket.getQuestionSections());
        List<ResourceRecord> finalAnswers = new ArrayList<>();
        List<ResourceRecord> finalAuthorities = new ArrayList<>();
        List<ResourceRecord> finalAdditionals = new ArrayList<>();
        if (questions == null) {
            return finalResponse.toBytes();
        }
        // if multiple question sections: one query per question section ->set qdcount to 1
        queryPacket.setQdcount(1);
        for (QuestionSection question : questions) {
            // resolve one question.
            DNSPacket singleResponse =
                    resolve(question.getName(), question.getQtype(), queryPacket, ResolveContext.start());
            System.out.println(singleResponse.toString());
            // add all RRs received as an/ar/ad to final answer
            Collections.addAll(finalAnswers, singleResponse.getAnswerSections());
            Collections.addAll(finalAuthorities, singleResponse.getAuthoritySections());
            Collections.addAll(finalAdditionals, singleResponse.getAdditionalSections());
        }
        finalResponse.setAnswerSections(finalAnswers.toArray(new ResourceRecord[0]));
        finalResponse.setAdditionalSections(finalAdditionals.toArray(new ResourceRecord[0]));
        finalResponse.setAuthoritySections(finalAuthorities.toArray(new ResourceRecord[0]));

        System.out.println(finalResponse.toString());
        return finalResponse.toBytes();
    }

    // Re-entrant core. Resolves sname/qtype from a fresh SLIST.
    // Terminates because the SLIST strictly shrinks:
    // - each pass either returns or removes a server.
    // Step 2 keeps this property:
    // - referrals prune higher-level
    // servers (so delegations only descend), and CNAME loops get a visited-set.
    private DNSPacket resolve(String[] sname, int qtype, DNSPacket queryPacket, ResolveContext ctx) {
        // OOP QueryPacket for logging
        byte[] query = queryPacket.toBytes();

        // Cap glue/CNAME resolution to avoid infinite recursion.
        if (ctx.depth() > MAX_DEPTH) {
            return buildServFailPacket(queryPacket);
        }

        // fill slist
        SListWrapper slistWrap;
        // Do not cache-seed nested lookups
        // This way failed nested delegation don't poison the next ns lookup
        if (ctx.depth() > 0) {
            slistWrap = new SListWrapper(sname);
        } else {
            try {
                slistWrap = new SListWrapper(sname, cache);
            } catch (SQLException e) {
                slistWrap = new SListWrapper(sname);
            }
        }

        while (true) {

            //  find the best server
            SListEntry bestNsserver = slistWrap.getBestServer();
            // SLIST empty.
            if (bestNsserver == null
                    || (bestNsserver.ipv4().toString().equals("0.0.0.0")
                            && bestNsserver.ipv6().toString().equals("0000:0000:0000:0000:0000:0000:0000:0000"))
                    || bestNsserver.nsName().getNsdname().length == 0) {
                return buildServFailPacket(queryPacket);
            }

            // build NameServer for queryUDP/queryTCP
            String ipv4Str = bestNsserver.ipv4().toString();
            String ipv6Str = bestNsserver.ipv6().toString();
            String nsName = bestNsserver.nsName().toString();

            NameServer queriedServerIpv4 = new NameServer(ipv4Str, 53, nsName);
            NameServer queriedServerIpv6 = new NameServer(ipv6Str, 53, nsName);

            // send iterative query
            QueryResult result = null;
            NameServer currentServer = null;
            // query UDP: try ipv4, if not available use ipv6
            if (!queriedServerIpv4.host().equals("0.0.0.0")) {
                currentServer = queriedServerIpv4;
                logger.logTrace("Sending query to " + currentServer.name(), queryPacket);
                result = queryUDP(query, queriedServerIpv4, timeoutMs);
            } else if (!queriedServerIpv6.host().equals("0000:0000:0000:0000:0000:0000:0000:0000")) {
                currentServer = queriedServerIpv6;
                logger.logTrace("Sending query to " + currentServer.name(), queryPacket);
                result = queryUDP(query, queriedServerIpv6, timeoutMs);
            }

            // handle failures: new while iteration with new server
            if (result == null || result.status() == Status.TIMEOUT || result.status() == Status.IO_ERROR) {
                sleep(retryDelay);
                logger.logTrace("Query failure, retry", queryPacket);
                continue;
            }

            // fallback to TCP in case of truncation
            if (isTruncated(result.response())) {
                logger.logTrace("Query truncated, retrying over TCP", queryPacket);
                QueryResult tcpResult = queryTCP(query, currentServer, timeoutMs);
                // handle failures: new while iteration with new server
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
                        LoggingSeverity.ERROR);
            }

            // which branch of the RCODE decision tree is this?
            ReferralAnalyzer.Classification verdict = ReferralAnalyzer.classify(response, qtype);
            if (verdict == ReferralAnalyzer.Classification.NODATA
                    && !response.isAa()
                    && response.getAnswerSections().length == 0
                    && response.getAuthoritySections().length > 0) {
                verdict = ReferralAnalyzer.Classification.REFERRAL;
            }

            switch (verdict) {
                // The response is the answer, a final SOA, or a DNS error: return it.
                case ANSWER, NODATA, NXDOMAIN -> {
                    logger.logTrace("Recursive resolver has finished successfully", queryPacket);
                    return response;
                }
                // This server could not answer: try the next one.
                case SERVFAIL, FORMERR, REFUSED -> {
                    logger.logTrace("Server returned " + verdict + ", retry", queryPacket);
                    continue;
                }
                // Delegation: resolve any missing glue, add the servers, keep walking down.
                case REFERRAL -> {
                    boolean addedAny = false;

                    List<Delegation> delegations = extractDelegations(response);
                    // PASS 1: first add referrals that already have glue.
                    for (Delegation d : delegations) {
                        if (!d.hasGlue()) {
                            continue;
                        }
                        slistWrap.addSListEntry(d.ns(), d.ipv4(), d.ipv6());
                        addedAny = true;
                        logger.logTrace(
                                "Received glueful referral from: " + currentServer.name() + " to: " + d.toString(),
                                queryPacket);
                    }

                    // PASS 2: only if no glueful referral was usable, resolve glue-less NS names.
                    if (!addedAny) {
                        logger.logTrace("No glueful referrals received, try glueless referalls: ", queryPacket);
                        for (Delegation d : delegations) {
                            if (d.hasGlue()) {
                                continue;
                            }
                            logger.logTrace(
                                    "Received glueless referral from: " + currentServer.name() + " to: "
                                            + String.join(".", ((RDataNS) d.ns.getRdata()).getNsdname())
                                            + ". resolving:",
                                    queryPacket);
                            Delegation resolved = resolveNsAddress(d, ctx.deeper());
                            if (!resolved.hasGlue()) {
                                logger.logTrace(
                                        "No gluerecord found for: "
                                                + String.join(".", ((RDataNS) d.ns.getRdata()).getNsdname())
                                                + ". continue to next referral",
                                        queryPacket);
                                continue;
                            }

                            logger.logTrace("Received gluerecord for: " + resolved.toString(), queryPacket);
                            slistWrap.addSListEntry(resolved.ns(), resolved.ipv4(), resolved.ipv6());
                            addedAny = true;

                            // One usable NS is enough. Go back to resolving the original name.
                            break;
                        }
                    }
                    if (!addedAny) {
                        return buildServFailPacket(queryPacket);
                    }
                    continue;
                }
                // Alias: restart at the canonical name, then prepend the CNAME we followed.
                case CNAME_CHAIN -> {
                    String[] target = cnameTarget(response);
                    if (target == null) {
                        return buildServFailPacket(queryPacket);
                    }
                    // loop guard: a target we have already followed means a CNAME loop
                    logger.logTrace(
                            "Received CNAME for: " + String.join(".", sname) + ". to: " + String.join(".", target)
                                    + ".",
                            queryPacket);
                    if (!ctx.cnamesSeen().add(String.join(".", target))) {
                        return buildServFailPacket(queryPacket);
                    }
                    // resolve the target via cache (following cached CNAMEs) then iterate the tail
                    return prependCnames(response, resolveCnameTarget(target, qtype, ctx));
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

        if (ctx.depth() >= MAX_NS_ADDRESS_DEPTH) {
            return d;
        }

        RData a = resolveAddress(nsName, RRType.A, ctx);
        if (a != null) {
            return new Delegation(d.ns(), (RDataA) a, new RDataAAAA(new byte[16]));
        }
        return d;
    }

    /*
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

     */

    /**
     * Looks up an address record for a name, cache first then iterative.
     *
     * @param name the name server's host name
     * @param type the address type (A or AAAA)
     * @param ctx the resolution context
     * @return the matching address rdata, or null if it cannot be resolved
     */
    RData resolveAddress(String[] name, int type, ResolveContext ctx) {
        try {
            ResourceRecord[] cached = cache.lookup(String.join(".", name), type);
            if (cached.length > 0) {
                return cached[0].getRdata();
            }
        } catch (SQLException e) {
            // cache unavailable: fall through to an iterative lookup
            logger.log(
                    (RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage()
                            + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO);
        }
        DNSPacket parsed = resolve(name, type, buildQuery(name, type), ctx.deeper());
        if (parsed.getAnswerSections() != null) {
            for (ResourceRecord rr : parsed.getAnswerSections()) {
                if (rr.getType() == type) {
                    return rr.getRdata();
                }
            }
            // Add CNAME handling, might help?
            for (ResourceRecord rr : parsed.getAnswerSections()) {
                if (rr.getType() == RRType.CNAME) {
                    String[] cname = ((RDataCNAME) rr.getRdata()).getCname();
                    return resolveAddress(cname, type, ctx.deeper());
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
     * Resolves a name using the cache as far as it reaches: it follows cached CNAME records and only resolves the
     * remaining tail of the chain iteratively.
     *
     * @param name the name to resolve
     * @param qtype the wanted record type
     * @param ctx the resolution context
     * @return a response, cached CNAMEs prepended onto the resolved tail
     */
    DNSPacket resolveCnameTarget(String[] name, int qtype, ResolveContext ctx) {
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
        return resolve(name, qtype, buildQuery(name, qtype), ctx.deeper());
    }

    /**
     * A response built from cached records for the name and type, or null on a cache miss.
     *
     * @param name the name to look up
     * @param qtype the record type
     * @return a NOERROR response with the cached records, or null if not cached
     */
    DNSPacket cachedResponse(String[] name, int qtype) {
        ResourceRecord[] cached;
        try {
            cached = cache.lookup(String.join(".", name), qtype);
        } catch (SQLException e) {
            // treat a cache error like a miss so the caller falls back to iterating
            logger.log(
                    (RED + "[RECURSIVE] cache lookup failed for " + String.join(".", name) + ": " + e.getMessage()
                            + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO);
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
        packet.setQuestionSections(new QuestionSection[] {question});
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
    private DNSPacket buildQuery(String[] name, int qtype) {
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
        packet.setQuestionSections(new QuestionSection[] {question});
        packet.setAnswerSections(new ResourceRecord[0]);
        packet.setAuthoritySections(new ResourceRecord[0]);
        packet.setAdditionalSections(new ResourceRecord[0]);
        packet.setEdnsRecord(new OPTPseudoResourceRecord(ednsUdpSizeLimit, 0, 0, false, new RDataOPTOption()));
        return packet;
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

        @Override
        public String toString() {
            String nsName = String.join(".", ((RDataNS) ns.getRdata()).getNsdname()) + ".";

            String ipv4Text = isZero(ipv4.getAddress()) ? "none" : ipv4.toString();
            String ipv6Text = isZero(ipv6.getAddress()) ? "none" : ipv6.toString();

            return "ns=" + nsName + " ipv4=" + ipv4Text + " ipv6=" + ipv6Text;
        }
    }

    /**
     * Pulls the NS records from the authority section and matches their glue (A / AAAA) from the additional section.
     * One Delegation per NS record.
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

            List<RDataA> ipv4Glue = findAllGlueA(additional, nsHost);
            List<RDataAAAA> ipv6Glue = findAllGlueAAAA(additional, nsHost);

            if (!ipv4Glue.isEmpty()) {
                for (RDataA a : ipv4Glue) {
                    delegations.add(new Delegation(rr, a, new RDataAAAA(new byte[16])));
                }
                continue;
            }

            if (!ipv6Glue.isEmpty()) {
                for (RDataAAAA aaaa : ipv6Glue) {
                    delegations.add(new Delegation(rr, new RDataA(new byte[4]), aaaa));
                }
                continue;
            }

            delegations.add(new Delegation(rr, new RDataA(new byte[4]), new RDataAAAA(new byte[16])));
        }
        return delegations;
    }

    private static List<RDataA> findAllGlueA(ResourceRecord[] additional, String nsHost) {
        List<RDataA> out = new ArrayList<>();
        if (additional == null) {
            return out;
        }
        for (ResourceRecord rr : additional) {
            if (rr.getType() == RRType.A && String.join(".", rr.getName()).equalsIgnoreCase(nsHost)) {
                out.add((RDataA) rr.getRdata());
            }
        }
        return out;
    }

    private static List<RDataAAAA> findAllGlueAAAA(ResourceRecord[] additional, String nsHost) {
        List<RDataAAAA> out = new ArrayList<>();
        if (additional == null) {
            return out;
        }
        for (ResourceRecord rr : additional) {
            if (rr.getType() == RRType.AAAA && String.join(".", rr.getName()).equalsIgnoreCase(nsHost)) {
                out.add((RDataAAAA) rr.getRdata());
            }
        }
        return out;
    }

    /**
     * probably obsolete? Finds the glue rdata of a given type for a name server in the additional section.
     *
     * @param additional the additional section of a response (may be null)
     * @param nsHost the name server's host name
     * @param type the record type to look for (A or AAAA)
     * @return the matching rdata, or null if none is present
     */
    private static RData findGlue(ResourceRecord[] additional, String nsHost, int type) {
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

    private DNSPacket buildServFailPacket(DNSPacket queryPacket) {
        DNSPacket servFailPacket = new DNSPacket();
        servFailPacket.setId(queryPacket.getId());
        servFailPacket.setQr(true);
        servFailPacket.setOpcode(queryPacket.getOpcode());
        servFailPacket.setRcode(2);
        servFailPacket.setQuestionSections(queryPacket.getQuestionSections());
        servFailPacket.setAnswerSections(new ResourceRecord[0]);
        servFailPacket.setAuthoritySections(new ResourceRecord[0]);
        servFailPacket.setAdditionalSections(new ResourceRecord[0]);
        return servFailPacket;
    }
}
