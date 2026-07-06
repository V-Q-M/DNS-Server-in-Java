package io.gitlab.teamproject2026.server;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.RecursiveResolver;
import io.gitlab.teamproject2026.resolver.ReferralAnalyzer;
import io.gitlab.teamproject2026.resolver.StubResolver;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DNSResolutionPipeline {
    private static final int MAX_CNAME_HOPS = 8;
    /** Per-RRset rotation counters keyed by "name|type", so each RRset round-robins independently. */
    private final Map<String, AtomicInteger> rrsetRotations = new ConcurrentHashMap<>();

    private final CacheHandler cacheHandler;
    private final ZoneHandler zoneHandler;
    private final StubResolver stubResolver;
    private final RecursiveResolver recursiveResolver;
    private final Logger logger;
    private final List<String> zoneCatalog;
    private final boolean minimalResponses;
    private final ForwardMode forwardMode;
    private final int ednsUdpSizeLimit;
    final ResolverSection resolverOptions;

    private long startTime;

    public DNSResolutionPipeline(
            CacheHandler cacheHandler,
            ZoneHandler zoneHandler,
            ResolverSection resolverOptions,
            StubResolver stubResolver,
            RecursiveResolver recursiveResolver,
            Logger logger,
            List<String> zoneCatalog,
            boolean minimalResponses,
            ForwardMode forwardMode,
            long startTime,
            int ednsUdpSizeLimit) {
        this.cacheHandler = cacheHandler;
        this.zoneHandler = zoneHandler;
        this.resolverOptions = resolverOptions;
        this.stubResolver = stubResolver;
        this.recursiveResolver = recursiveResolver;
        this.logger = logger;
        this.zoneCatalog = zoneCatalog;
        this.minimalResponses = minimalResponses;
        this.forwardMode = forwardMode;
        this.startTime = startTime;
        this.ednsUdpSizeLimit = ednsUdpSizeLimit;
    }

    /**
     * Round-robin load balancing: returns the authoritative RRset reordered so a different record leads each response.
     * The input list is not modified.
     */
    List<ResourceRecord> roundRobin(List<ResourceRecord> records, String name, int qtype) {
        return switch (qtype) {
            case RRType.A, RRType.AAAA, RRType.NS ->
                rotate(records, name + "|" + qtype); // all equivalent: rotate the whole set
            case RRType.MX -> roundRobinMx(records, name); // keep preference order, rotate equal-preference groups
            default -> records; // other types have no load-balancing semantics
        };
    }

    /**
     * Returns a copy of the group rotated one step (per-key counter), or the group unchanged if there is nothing to
     * rotate.
     */
    private List<ResourceRecord> rotate(List<ResourceRecord> group, String key) {
        if (group.size() < 2) {
            return group;
        }
        AtomicInteger counter = rrsetRotations.computeIfAbsent(key, k -> new AtomicInteger());
        List<ResourceRecord> rotated = new ArrayList<>(group);
        Collections.rotate(rotated, -Math.floorMod(counter.getAndIncrement(), rotated.size()));
        return rotated;
    }

    /** MX order by preference is required (RFC 5321 §5.1), so only rotate within each equal-preference group. */
    private List<ResourceRecord> roundRobinMx(List<ResourceRecord> records, String name) {
        Map<Integer, List<ResourceRecord>> byPreference = new TreeMap<>();
        for (ResourceRecord rr : records) {
            byPreference
                    .computeIfAbsent(((RDataMX) rr.getRdata()).getPreference(), k -> new ArrayList<>())
                    .add(rr);
        }
        List<ResourceRecord> result = new ArrayList<>(records.size());
        byPreference.forEach((preference, group) -> result.addAll(rotate(group, name + "|MX|" + preference)));
        return result;
    }

    public DNSPayload zoneLookUp(DNSPacket packet, String id) {
        DNSPayload payload =
                new DNSPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
        for (QuestionSection questionSection : packet.getQuestionSections()) {
            // Relevant for CNAME resolution
            String currentName = questionSection.getNameString().toLowerCase();
            Set<String> visited = new HashSet<>();

            // Per RFC1034 4.3.2 step 3a: loop to follow CNAME chains.
            // Each iteration is one restart of the algorithm with a potentially new QNAME.
            // MAX_CNAME_HOPS guards against excessively long chains.
            while (visited.size() < MAX_CNAME_HOPS && visited.add(currentName)) {

                // Step 2: find the most specific authoritative zone for currentName
                final String lookupName = currentName;
                String zone = zoneCatalog.stream()
                        .filter(e -> lookupName.equals(e) || lookupName.endsWith("." + e))
                        .max(Comparator.comparingInt(String::length))
                        .orElse(null);

                if (zone == null) {
                    // No authoritative zone for this name → cache/forward handle it
                    QuestionSection fwdQuestion = new QuestionSection();
                    fwdQuestion.setName(
                            currentName.split("\\.")); // This must use CurrentName, to account for CNAME resolution
                    fwdQuestion.setQtype(questionSection.getQtype());
                    fwdQuestion.setQclass(questionSection.getQclass());
                    payload.notResolved().add(fwdQuestion);
                    break;
                }

                // Step 3: we are authoritative for this domain
                try {
                    List<ResourceRecord> directMatches = roundRobin(
                            zoneHandler.lookup(currentName, questionSection.getQtype(), zone),
                            currentName,
                            questionSection.getQtype());

                    if (!directMatches.isEmpty()) {
                        // Step 3a: records found -> route to correct section per RFC1034 4.3.2

                        logger.log(
                                (DNSEvent.event(
                                                DNSEventType.SUCCESS,
                                                id,
                                                currentName,
                                                questionSection.getQtype(),
                                                0L,
                                                ResponseSource.ZONE,
                                                resolverOptions.strategy().toString(),
                                                zone,
                                                "Local Zone",
                                                1,
                                                null,
                                                -1)
                                        .toHumanReadable()),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.INFO);

                        for (ResourceRecord record : directMatches) {
                            switch (record.getType()) {
                                case RRType.NS -> {
                                    if (questionSection.getQtype() == RRType.NS) {
                                        payload.answer().add(record);
                                    } else {
                                        // NS records go to authority section, consistent with queryCache
                                        payload.authority().add(record);
                                    }
                                    // Step 6: add glue A/AAAA for the nameserver to additional
                                    if (!minimalResponses) {
                                        String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                        try {
                                            payload.additional().addAll(zoneHandler.lookup(nsHost, RRType.A, zone));
                                            payload.additional().addAll(zoneHandler.lookup(nsHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) {
                                            /* glue records are best-effort */
                                        }
                                    }
                                }
                                case RRType.MX -> {
                                    // MX records go to answer section
                                    payload.answer().add(record);
                                    // Step 6: add A/AAAA of mail exchange to additional
                                    if (!minimalResponses) {
                                        String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                        try {
                                            payload.additional().addAll(zoneHandler.lookup(mxHost, RRType.A, zone));
                                            payload.additional().addAll(zoneHandler.lookup(mxHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) {
                                            /* additional records are best-effort */
                                        }
                                    }
                                }
                                // A, AAAA, CNAME, TXT, SOA, PTR -> directly to answer
                                default -> payload.answer().add(record);
                            }
                        }
                        // If DNSSEC is desired, add the RRSIG records covering that type, if they exist
                        if (packet.getEdnsRecord() != null) {
                            logger.log(
                                    ("[ZONE] Checking for dnssec..."),
                                    LoggingCategoryType.GENERAL,
                                    LoggingSeverity.DEBUG,
                                    true);
                            logger.log(
                                    ("[ZONE] DNSSEC = " + packet.getEdnsRecord().getDNSSecOK()),
                                    LoggingCategoryType.GENERAL,
                                    LoggingSeverity.DEBUG,
                                    true);
                        }
                        if (packet.getEdnsRecord() != null
                                && packet.getEdnsRecord().getDNSSecOK()) {
                            List<ResourceRecord> signatures = zoneHandler.lookup(currentName, RRType.RRSIG, zone);
                            for (ResourceRecord signature : signatures) {
                                if (signature.getRdata() instanceof RDataRRSIG rrsig) {
                                    if (rrsig.getTypeCovered() == questionSection.getQtype()) {
                                        payload.answer().add(signature);
                                    }
                                }
                            }
                        }
                        logger.log(
                                (GREEN + "[ZONE] Success! Found: \"" + currentName + "\" in zone: \"" + zone + "\""
                                        + RESET),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.INFO,
                                true);
                        break; // records found -> done with this question
                    }

                    // No direct match -> check for CNAME (skip if QTYPE=CNAME, that is already answered above)
                    if (questionSection.getQtype() != RRType.CNAME) {
                        List<ResourceRecord> cnameMatches = zoneHandler.lookup(currentName, RRType.CNAME, zone);

                        if (!cnameMatches.isEmpty()) {
                            // Per RFC1034 4.3.2 step 3a: copy CNAME to answer,
                            // then go back to step 1 with the new QNAME (= restart the while loop)
                            payload.answer().addAll(cnameMatches);
                            currentName = String.join(
                                            ".",
                                            ((RDataCNAME) cnameMatches
                                                            .getFirst()
                                                            .getRdata())
                                                    .getCname())
                                    .toLowerCase();
                            continue; //  restart = "go back to step 1" per RFC
                        }
                    }

                    // No record and no CNAME -> NXDOMAIN.
                    // Question intentionally not added to notResolved; the caller detects
                    // NXDOMAIN via (notResolved.isEmpty && answer.isEmpty && authority.isEmpty).
                    logger.log(
                            (YELLOW + "[ZONE] Warning! Zone: \"" + zone + "\" is responsible for: \"" + currentName
                                    + "\" but has no entry for it" + RESET),
                            LoggingCategoryType.QUERY_ERRORS,
                            LoggingSeverity.WARNING,
                            true);
                    break;

                } catch (SQLException e) {
                    logger.log(
                            (RED
                                    + "[DATABASE-ERROR] Connection to database Server has been lost, assume internal Server error"
                                    + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true);
                    payload.notResolved().add(questionSection); // fall back to cache/forward
                    logger.log(
                            (DNSEvent.event(
                                            DNSEventType.CACHE_ERROR,
                                            null,
                                            null,
                                            -1,
                                            System.currentTimeMillis() - startTime,
                                            ResponseSource.CACHE,
                                            null,
                                            null,
                                            null,
                                            -1,
                                            "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error",
                                            -1)
                                    .toHumanReadable()),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR);
                    break;
                }
            }
            // If loop exits because visited.add(currentName) = false -> CNAME loop detected.
            // CNAMEs found so far remain in answer; stub resolver will handle the rest.
        }
        return payload;
    }

    public DNSPayload cacheLookUp(DNSPayload payload, OPTPseudoResourceRecord ednsRecord, String id) {
        // query cache and safe results in payload
        long cacheStart = System.currentTimeMillis();

        // Only query cache for questions not already answered by zone lokup
        // This preserves zone answers and prevents forwardigns authoritative domains
        DNSPacket unresolvedPacket = new DNSPacket();
        unresolvedPacket.setQuestionSections(payload.notResolved().toArray(new QuestionSection[0]));
        if (ednsRecord != null) {
            unresolvedPacket.setEdnsRecord(ednsRecord);
        }

        try {
            DNSPayload cacheResult = queryCache(unresolvedPacket);
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                String domain = request.nameToString();
                int qType = request.getQtype();
                String qTypeStr = RRType.mapToString(qType) != null ? RRType.mapToString(qType) : "TYPE" + qType;
                if (cacheResult.notResolved().contains(request)) {
                    logger.log(
                            (BLUE + "[CACHE] Miss! Couldn't find: " + qTypeStr + " of \"" + domain + "\"" + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.INFO,
                            true);
                    logger.log(
                            (DNSEvent.event(
                                            DNSEventType.CACHE_MISS,
                                            id,
                                            domain,
                                            qType,
                                            0L,
                                            ResponseSource.CACHE,
                                            resolverOptions.strategy().toString(),
                                            "Local Cache",
                                            "127.0.0.1",
                                            1,
                                            "Not found in database",
                                            -1)
                                    .toHumanReadable()),
                            LoggingCategoryType.QUERIES,
                            LoggingSeverity.NOTICE);

                } else {
                    logger.log(
                            (GREEN + "[CACHE] Hit! Found: " + qTypeStr + " Record of " + domain + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true);

                    logger.log(
                            (DNSEvent.event(
                                            DNSEventType.CACHE_HIT,
                                            id,
                                            domain,
                                            qType,
                                            0L,
                                            ResponseSource.CACHE,
                                            resolverOptions.strategy().toString(),
                                            "Local Cache",
                                            "127.0.0.1",
                                            1,
                                            null,
                                            -1)
                                    .toHumanReadable()),
                            LoggingCategoryType.QUERIES,
                            LoggingSeverity.INFO);
                }
            }
            // Merge cache results with existing zone answers
            payload.answer().addAll(cacheResult.answer());
            payload.authority().addAll(cacheResult.authority());
            payload.additional().addAll(cacheResult.additional());
            return new DNSPayload(
                    payload.answer(), payload.authority(), payload.additional(), cacheResult.notResolved());

        } catch (SQLException e) {
            // log
            logger.log(
                    (DNSEvent.event(
                                    DNSEventType.CACHE_ERROR,
                                    null,
                                    null,
                                    -1,
                                    System.currentTimeMillis() - cacheStart,
                                    ResponseSource.CACHE,
                                    null,
                                    null,
                                    null,
                                    -1,
                                    "Connection to cache Server has been lost, assume internal Server error",
                                    -1)
                            .toHumanReadable()),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true);
            // payload.notResolved already has the unresolved questions from zone lookup,
            // no need to add them again – just log them as misses for the stub resolver
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                logger.log(
                        (DNSEvent.event(
                                        DNSEventType.CACHE_MISS,
                                        id,
                                        request.nameToString(),
                                        request.getQtype(),
                                        System.currentTimeMillis() - cacheStart,
                                        ResponseSource.CACHE,
                                        resolverOptions.strategy().toString(),
                                        "Local Cache",
                                        "127.0.0.1",
                                        1,
                                        "cache couldn't be reached",
                                        -1)
                                .toHumanReadable()),
                        LoggingCategoryType.QUERIES,
                        LoggingSeverity.NOTICE);
            }
        }
        return payload;
    }

    // Cache internal logic

    /**
     * Queries the Cache using a given cacheHandler and a dns query. If the Cache contains the requested records, it
     * returns them in the corresponding section of the Record. Also Returns A and AAAA records for NS and MX records
     * Packets that couldn't be resolved with the cache itself are stored in the notResolved part of the record
     *
     * @param query the DNS query which is to be resolved cache
     * @return A DNSPayload record containing the resolved Records and the Records that have yet to be resolved
     * @throws SQLException if the cache cannot be accessed
     */
    public DNSPayload queryCache(DNSPacket query) throws SQLException {

        LinkedList<ResourceRecord> answer = new LinkedList<>();
        LinkedList<ResourceRecord> authority = new LinkedList<>();
        LinkedList<ResourceRecord> additional = new LinkedList<>();
        LinkedList<QuestionSection> notCached = new LinkedList<>();

        try {

            // for each query, check if the requested RR is cached, if not add to the
            // Queries sent to the StubResolver
            for (QuestionSection request : query.getQuestionSections()) {
                // Signatures can be cached sparsely. Better to resolve fresh when asking for all RRSIG
                if (request.getQtype() == RRType.RRSIG) {
                    notCached.add(request);
                    continue;
                }
                String qname = String.join(".", request.getName());
                ResourceRecord[] cachedRecords = cacheHandler.lookup(qname, request.getQtype());

                if (cachedRecords.length != 0) {
                    for (ResourceRecord record : cachedRecords) {
                        switch (record.getType()) {
                            case RRType.NS: {
                                if (request.getQtype() == RRType.NS) {
                                    answer.add(record);
                                } else {
                                    authority.add(record);
                                }
                                if (!minimalResponses) {
                                    String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                    Collections.addAll(additional, cacheHandler.lookup(nsHost, RRType.A));
                                    Collections.addAll(additional, cacheHandler.lookup(nsHost, RRType.AAAA));
                                }
                                break;
                            }
                            case RRType.MX: {
                                answer.add(record);
                                if (!minimalResponses) {
                                    String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                    Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.A));
                                    Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.AAAA));
                                }
                                break;
                            }
                            default:
                                answer.add(record);
                                break;
                        }
                    }
                    // If DNSSEC is desired, add the RRSIG records covering that type, if they exist
                    if (query.getEdnsRecord() != null) {
                        logger.log(
                                ("[CACHE] Checking for dnssec..."),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.DEBUG,
                                true);
                        logger.log(
                                ("[CACHE] DNSSEC = " + query.getEdnsRecord().getDNSSecOK()),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.DEBUG,
                                true);
                    }
                    if (query.getEdnsRecord() != null && query.getEdnsRecord().getDNSSecOK()) {
                        ResourceRecord[] signatures = cacheHandler.lookup(qname, RRType.RRSIG);
                        if (signatures.length == 0) {
                            answer.clear();
                            notCached.add(request);
                            continue;
                        }
                        for (ResourceRecord signature : signatures) {
                            if (signature.getRdata() instanceof RDataRRSIG rrsig) {
                                if (rrsig.getTypeCovered() == request.getQtype()) {
                                    answer.add(signature);
                                    break;
                                }
                            }
                        }
                    }
                } else if (request.getQtype() != RRType.CNAME) {
                    // CNAME-type queries are handled by the direct lookup above
                    // this branch only follows an alias when a different record type was requested
                    if (!resolveCnameFromCache(qname, request.getQtype(), request.getQclass(), answer, notCached)) {
                        notCached.add(request);
                    }
                } else {
                    notCached.add(request);
                }
            }
        } catch (SQLException e) {
            logger.log(
                    (RED
                            + "[DATABASE-ERROR] Connection to Server couldn't be established. Assume internal Server error. Is the Postgres-Docker running?"
                            + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.ERROR,
                    true);
            throw e;
        }

        return new DNSPayload(answer, authority, additional, notCached);
    }

    // Follows a CNAME chain for "name" through the cache, hop by hop, until it reaches a
    // record of the requested targetType (e.g. A). Three outcomes:
    //
    //   full hit    - the whole chain AND the final target records are cached:
    //                 append them all to "answer" and return true.
    //   partial hit - the alias(es) are cached but the final target is not:
    //                 append the cached CNAME(s) to "answer" and add the still-missing name
    //                 to "notResolved" so the stub only resolves the remaining tail; return true.
    //   no help     - return false and change nothing, so the caller forwards the original
    //                 query. Happens when "name" has no cached CNAME, the chain loops, or it
    //                 is longer than MAX_CNAME_HOPS.
    private boolean resolveCnameFromCache(
            String name,
            int targetType,
            int targetClass,
            LinkedList<ResourceRecord> answer,
            LinkedList<QuestionSection> notCached)
            throws SQLException {
        String current = name.toLowerCase();
        LinkedList<ResourceRecord> chain = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        for (int hop = 0; hop < MAX_CNAME_HOPS; hop++) {
            // visited.add returns false if current was already seen -> CNAME loop,
            // bail out (committing nothing) and let the StubResolver handle it
            if (!visited.add(current)) return false;

            ResourceRecord[] cnames = cacheHandler.lookup(current, RRType.CNAME);
            if (cnames.length == 0) {
                // Reached the end of the cached chain.
                if (chain.isEmpty()) return false; // no alias at the queried name -> forward whole query
                // Partial hit: the alias(es) are cached but the terminal record is not.
                // Commit the cached CNAME(s) and forward only the unresolved tail, so the
                // chain is always completed by the stub (never a dangling CNAME-only answer).
                answer.addAll(chain);
                QuestionSection tail = new QuestionSection();
                tail.setName(current.split("\\."));
                tail.setQtype(targetType);
                tail.setQclass(targetClass);
                notCached.add(tail);
                return true;
            }

            chain.add(cnames[0]);
            String target = String.join(".", ((RDataCNAME) cnames[0].getRdata()).getCname())
                    .toLowerCase();

            ResourceRecord[] targetRecords = cacheHandler.lookup(target, targetType);
            if (targetRecords.length > 0) {
                // Full hit: the whole chain and its terminal records are cached.
                answer.addAll(chain);
                Collections.addAll(answer, targetRecords);
                return true;
            }

            current = target;
        }

        // Chain longer than MAX_CNAME_HOPS without resolving
        // forward the whole query to the stub
        return false;
    }

    public DNSPayload resolveUpstream(DNSPayload payload, OPTPseudoResourceRecord ednsRecord, byte[] data) {
        if (payload.notResolved().isEmpty()) {
            return payload;
        }
        // Build packet to be sent to Stub/recursive resolver
        // The ID should not have to be changed, as the Server should not receive any
        // other packages using this ID from the original sender
        DNSPacket uncachedOOPacket = DNSPacket.fromBytes(data);
        uncachedOOPacket.setQuestionSections(payload.notResolved().toArray(new QuestionSection[0]));
        uncachedOOPacket.setQdcount(payload.notResolved().size());

        // Always attach our own EDNS OPT record queries to upstream servers
        // even when original query doesn't use edns
        // this way we can always receive larger UDP responses from upstream
        // uncachedOOPacket.setEdnsRecord(
        //        new OPTPseudoResourceRecord(ednsUdpSizeLimit, 0, 0, false, new RDataOPTOption()));
        if (ednsRecord != null) {
            uncachedOOPacket.setEdnsRecord(ednsRecord);
        }

        // Get Stub/recursive response
        byte[] rawResponse = null;
        if (forwardMode == null) {
            try {
                logger.logTrace("Resolve recursively", uncachedOOPacket);
                rawResponse = recursiveResolver.resolve(uncachedOOPacket.toBytes());
            } catch (RuntimeException e) {
                logger.log(
                        (RED + "[RECURSIVE-ERROR] Failed to resolve query" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true);
                return payload;
            }
        } else {
            switch (forwardMode) {
                case FORWARD_ONLY -> { // only stub
                    logger.logTrace("Forward via Stubresolver", uncachedOOPacket);
                    try {
                        rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
                    } catch (RuntimeException e) {
                        logger.log(
                                (RED + "[STUB-ERROR] Failed to resolve query" + RESET),
                                LoggingCategoryType.QUERY_ERRORS,
                                LoggingSeverity.ERROR,
                                true);
                        return payload;
                    }
                }
                case FORWARD_FIRST -> {
                    try {
                        // try stub
                        logger.logTrace("Try forwarding via Stubresolver", uncachedOOPacket);
                        rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
                    } catch (RuntimeException e) {
                        logger.log(
                                (YELLOW
                                        + "[SERVER] Forwarder failed or timed out in FORWARD_FIRST mode. Falling back to iterative resolution..."
                                        + RESET),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.WARNING,
                                true);
                    }
                    // if stub resolver fails: fallback recursive resolver
                    if (rawResponse == null) {
                        try {
                            logger.logTrace(
                                    "Stubresolver unsuccessful, try recursive Resolver instead", uncachedOOPacket);
                            rawResponse = recursiveResolver.resolve(uncachedOOPacket.toBytes());
                        } catch (RuntimeException e) {
                            logger.log(
                                    (RED + "[RECURSIVE-ERROR] Iterative fallback failed" + RESET),
                                    LoggingCategoryType.QUERY_ERRORS,
                                    LoggingSeverity.ERROR,
                                    true);
                            return payload;
                        }
                    }
                }
            }
        }

        DNSPacket parsedResponse = DNSPacket.fromBytes(rawResponse);
        // ----------------------------ReferralAnalyzer-------------------------------------------------
        int queryType = payload.notResolved().isEmpty()
                ? RRType.A
                : payload.notResolved().getFirst().getQtype();

        logger.log(
                ("[UPSTREAM] rcode=" + parsedResponse.getRcode() + " answers="
                        + parsedResponse.getAnswerSections().length + " qtype=" + queryType),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.DEBUG);
        // stub response
        ReferralAnalyzer.Classification classification = ReferralAnalyzer.classify(parsedResponse, queryType);
        logger.log(
                ("[ANALYZER] " + classification + " type: " + queryType),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.INFO,
                true);
        switch (classification) {
            case ANSWER, CNAME_CHAIN, NODATA -> {
                // Extract RRs from Stub Response and cache them if cachHandler is available
                // caching
                logger.logTrace("Upstream resolving has finished successfully", uncachedOOPacket);
                if (cacheHandler != null) {
                    startTime = System.currentTimeMillis(); // set new startTime for logging of CACHE_ERROR
                    try {
                        cacheHandler.insertAll(parsedResponse.getAnswerSections());
                        cacheHandler.insertAll(parsedResponse.getAuthoritySections());
                        cacheHandler.insertAll(parsedResponse.getAdditionalSections());
                    } catch (SQLException e) {
                        logger.log(
                                ("[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost"),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR,
                                true);
                        logger.log(
                                (DNSEvent.event(
                                                DNSEventType.CACHE_ERROR,
                                                null,
                                                null,
                                                -1,
                                                System.currentTimeMillis() - startTime,
                                                ResponseSource.CACHE,
                                                null,
                                                null,
                                                null,
                                                -1,
                                                "[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost",
                                                -1)
                                        .toHumanReadable()),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR);
                    }
                }

                // extracting and adding to answers
                Collections.addAll(payload.answer(), parsedResponse.getAnswerSections());
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
                Collections.addAll(payload.additional(), parsedResponse.getAdditionalSections());

                payload.notResolved().clear();
            }
            case NXDOMAIN -> {
                logger.logTrace("NXDOMAIN received from upstream", uncachedOOPacket);
                logger.log(
                        (YELLOW + "[RESOLVER] NXDOMAIN received from upstream" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.WARNING,
                        true);
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
            }
            case REFERRAL -> {
                String referral = "";
                for (ResourceRecord ns : parsedResponse.getAuthoritySections()) {
                    referral += String.join(".", ((RDataNS) ns.getRdata()).getNsdname()) + ". , ";
                }
                logger.logTrace("Referral to: " + referral, uncachedOOPacket);

                logger.log(
                        (YELLOW + "[RESOLVER] Referral received" + RESET),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.INFO,
                        true);
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
                Collections.addAll(payload.additional(), parsedResponse.getAdditionalSections());
            }
            case SERVFAIL, FORMERR -> {
                logger.logTrace("Resolving unsuccessful", uncachedOOPacket);
                logger.log(
                        (RED + "[RESOLVER] upstream returned" + classification + ", skipping cache insert" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true);
            }
        }
        return payload;
    }
}
