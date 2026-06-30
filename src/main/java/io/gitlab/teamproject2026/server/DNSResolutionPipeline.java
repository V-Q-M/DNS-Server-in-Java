package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.RecursiveResolver;
import io.gitlab.teamproject2026.resolver.StubResolver;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;

import java.sql.SQLException;
import java.util.*;

public class DNSResolutionPipeline {
    private static final int MAX_CNAME_HOPS = 8;
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String PINK = "\u001B[95m";

    private final CacheHandler cacheHandler;
    private final ZoneHandler zoneHandler;
    private final StubResolver stubResolver;
    private final RecursiveResolver recursiveResolver;
    private final Logger logger;
    private final List<String> zoneCatalog;
    private final boolean minimalResponses;
    private final ForwardMode forwardMode;
    private final ResolverSection resolverOptions;

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
            long startTime
    ) {
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
    }

    public DNSPayload zoneLookUp(DNSPacket packet, String id) {
        DNSPayload payload = new DNSPayload(
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>()
        );
        for (QuestionSection questionSection : packet.getQuestionSections()) {
            String currentName = questionSection.getNameString().toLowerCase();
            Set<String> visited = new HashSet<>();

            // Per RFC1034 4.3.2 step 3a: loop to follow CNAME chains.
            // Each iteration is one restart of the algorithm with a potentially new QNAME.
            // MAX_CNAME_HOPS guards against excessively long chains.
            while (visited.size() < MAX_CNAME_HOPS && visited.add(currentName)) {

                // Step 2: find the most specific authoritative zone for currentName
                final String lookupName = currentName;
                String zone = zoneCatalog
                        .stream()
                        .filter(e -> lookupName.equals(e) || lookupName.endsWith("." + e))
                        .max(Comparator.comparingInt(String::length))
                        .orElse(null);

                if (zone == null) {
                    // No authoritative zone for this name → cache/forward handle it
                    QuestionSection fwdQuestion = new QuestionSection();
                    fwdQuestion.setName(currentName.split("\\."));
                    fwdQuestion.setQtype(questionSection.getQtype());
                    fwdQuestion.setQclass(questionSection.getQclass());
                    payload.notResolved().add(fwdQuestion);
                    break;
                }

                // Step 3: we are authoritative for this domain
                try {
                    List<ResourceRecord> directMatches = zoneHandler.lookup(currentName, questionSection.getQtype(), zone);

                    if (!directMatches.isEmpty()) {
                        // Step 3a: records found -> route to correct section per RFC1034 4.3.2

                        logger.log(
                                (DNSEvent.event(DNSEventType.SUCCESS, id, currentName, questionSection.getQtype(), 0L, ResponseSource.ZONE,
                                        resolverOptions.strategy().toString(), zone, "Local Zone", 1, null, null).toHumanReadable()),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.INFO
                        );

                        for (ResourceRecord record : directMatches) {
                            switch (record.getType()) {
                                case NS -> {
                                    // NS records go to authority section, consistent with queryCache
                                    payload.authority().add(record);
                                    // Step 6: add glue A/AAAA for the nameserver to additional
                                    if (!minimalResponses) {
                                        String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                        try {
                                            payload.additional().addAll(zoneHandler.lookup(nsHost, RRType.A, zone));
                                            payload.additional().addAll(zoneHandler.lookup(nsHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) { /* glue records are best-effort */ }
                                    }
                                }
                                case MX -> {
                                    // MX records go to answer section
                                    payload.answer().add(record);
                                    // Step 6: add A/AAAA of mail exchange to additional
                                    if (!minimalResponses) {
                                        String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                        try {
                                            payload.additional().addAll(zoneHandler.lookup(mxHost, RRType.A, zone));
                                            payload.additional().addAll(zoneHandler.lookup(mxHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) { /* additional records are best-effort */ }
                                    }
                                }
                                // A, AAAA, CNAME, TXT, SOA, PTR -> directly to answer
                                default -> payload.answer().add(record);
                            }
                        }
                        logger.log(
                                (GREEN + "[ZONE] Success! Found: \"" + currentName + "\" in zone: \"" + zone + "\"" + RESET),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.INFO,
                                true
                        );
                        break; // records found -> done with this question
                    }

                    // No direct match -> check for CNAME (skip if QTYPE=CNAME, that is already answered above)
                    if (questionSection.getQtype() != RRType.CNAME) {
                        List<ResourceRecord> cnameMatches = zoneHandler.lookup(currentName, RRType.CNAME, zone);

                        if (!cnameMatches.isEmpty()) {
                            // Per RFC1034 4.3.2 step 3a: copy CNAME to answer,
                            // then go back to step 1 with the new QNAME (= restart the while loop)
                            payload.answer().addAll(cnameMatches);
                            currentName = String.join(".",
                                    ((RDataCNAME) cnameMatches.getFirst().getRdata()).getCname()).toLowerCase();
                            continue; //  restart = "go back to step 1" per RFC
                        }
                    }

                    // No record and no CNAME -> NXDOMAIN.
                    // Question intentionally not added to notResolved; the caller detects
                    // NXDOMAIN via (notResolved.isEmpty && answer.isEmpty && authority.isEmpty).
                    logger.log(
                            (YELLOW + "[ZONE] Warning! Zone: \"" + zone + "\" is responsible for: \"" + currentName + "\" but has no entry for it" + RESET),
                            LoggingCategoryType.QUERY_ERRORS,
                            LoggingSeverity.WARNING,
                            true
                    );
                    break;

                } catch (SQLException e) {
                    logger.log(
                            (RED + "[DATABASE-ERROR] Connection to database Server has been lost, assume internal Server error" + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true
                    );
                    payload.notResolved().add(questionSection); // fall back to cache/forward
                    logCacheConnectionLost(startTime);
                    break;
                }
            }
            // If loop exits because visited.add(currentName) = false -> CNAME loop detected.
            // CNAMEs found so far remain in answer; stub resolver will handle the rest.
        }
        return payload;
    }

    public DNSPayload cacheLookUp(DNSPayload payload, DNSPacket packet, String id) {
        // query cache and safe results in payload
        long cacheStart = System.currentTimeMillis();

        // Only query cache for questions not already answered by zone lokup
        // This preserves zone answers and prevents forwardigns authoritative domains
        DNSPacket unresolvedPacket = new DNSPacket();
        unresolvedPacket.setQuestionSections(payload.notResolved().toArray(new QuestionSection[0]));

        try {
            DNSPayload cacheResult = queryCache(unresolvedPacket, cacheHandler);
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                String domain = request.nameToString();
                RRType qType = request.getQtype();
                if (cacheResult.notResolved().contains(request)) {
                    logger.log(
                            (BLUE + "[CACHE] Miss! Couldn't find: " + qType + " of \"" + domain + "\"" + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.INFO,
                            true
                    );
                    logger.log(
                            (DNSEvent.event(DNSEventType.CACHE_MISS, id, domain, qType, 0L, ResponseSource.CACHE, resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "Not found in database", null).toHumanReadable()),
                            LoggingCategoryType.QUERIES,
                            LoggingSeverity.NOTICE
                    );

                } else {
                    logger.log(
                            (GREEN + "[CACHE] Hit! Found: " + qType + " Record of " + domain + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true
                    );

                    logger.log(
                            (DNSEvent.event(DNSEventType.CACHE_HIT, id, domain, qType, 0L, ResponseSource.CACHE,
                                    resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, null, null).toHumanReadable()),
                            LoggingCategoryType.QUERIES,
                            LoggingSeverity.INFO
                    );
                }

            }
            // Merge cache results with existing zone answers
            payload.answer().addAll(cacheResult.answer());
            payload.authority().addAll(cacheResult.authority());
            payload.additional().addAll(cacheResult.additional());
            return new DNSPayload(payload.answer(), payload.authority(), payload.additional(), cacheResult.notResolved());

        } catch (SQLException e) {
            // log
            logger.log(
                    (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - cacheStart, ResponseSource.CACHE, null, null, null, -1,
                            "Connection to cache Server has been lost, assume internal Server error", null).toHumanReadable()),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            // payload.notResolved already has the unresolved questions from zone lookup,
            // no need to add them again – just log them as misses for the stub resolver
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                logger.log(
                        (DNSEvent.event(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), System.currentTimeMillis() - cacheStart, ResponseSource.CACHE,
                                resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "cache couldn't be reached", null).toHumanReadable()),
                        LoggingCategoryType.QUERIES,
                        LoggingSeverity.NOTICE
                );
            }
        }
        return payload;
    }


    // Cache internal logic

    /**
     * Queries the Cache using a given cacheHandler and a dns query. If the Cache
     * contains the requested records, it returns them in the corresponding section
     * of the Record.
     * Also Returns A and AAAA records for NS and MX records
     * Packets that couldn't be resolved with the cache itself are stored in the
     * notResolved part of the record
     *
     * @param query        the DNS query which is to be resolved
     * @param cacheHandler the CacheHandler with which the function accesses the
     *                     cache
     * @return A DNSPayload record containing the resolved Records and the Records
     *         that have yet to be resolved
     * @throws SQLException if the cache cannot be accessed
     */
    public DNSPayload queryCache(DNSPacket query, CacheHandler cacheHandler) throws SQLException {

        LinkedList<ResourceRecord> answer = new LinkedList<>();
        LinkedList<ResourceRecord> authority = new LinkedList<>();
        LinkedList<ResourceRecord> additional = new LinkedList<>();
        LinkedList<QuestionSection> notCached = new LinkedList<>();

        try {

            // for each query, check if the requested RR is cached, if not add to the
            // Queries sent to the StubResolver
            for (QuestionSection request : query.getQuestionSections()) {
                String qname = String.join(".", request.getName());
                ResourceRecord[] cachedRecords = cacheHandler.lookup(qname, request.getQtype());

                if (cachedRecords.length != 0) {
                    for (ResourceRecord record : cachedRecords) {
                        switch (record.getType()) {
                            case NS: {
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
                            case MX: {
                                answer.add(record);
                                if (!minimalResponses) {
                                    String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                    Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.A));
                                    Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.AAAA));
                                }
                                break;
                            }
                            case A, AAAA, TXT, SOA, PTR, CNAME:
                                answer.add(record);
                        }
                    }
                } else if (request.getQtype() != RRType.CNAME) {
                    // CNAME-type queries are handled by the direct lookup above
                    // this branch only follows an alias when a different record type was requested
                    if (!resolveCnameFromCache(qname, request.getQtype(), request.getQclass(), answer, notCached,
                            cacheHandler)) {
                        notCached.add(request);
                    }
                } else {
                    notCached.add(request);
                }
            }
        } catch (SQLException e) {
            logger.log(
                    (RED + "[DATABASE-ERROR] Connection to Server couldn't be established. Assume internal Server error. Is the Postgres-Docker running?" + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.ERROR,
                    true
            );
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
    private static boolean resolveCnameFromCache(
            String name, RRType targetType, RRClass targetClass,
            LinkedList<ResourceRecord> answer,
            LinkedList<QuestionSection> notCached,
            CacheHandler cacheHandler) throws SQLException {
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
            String target = String.join(".", ((RDataCNAME) cnames[0].getRdata()).getCname()).toLowerCase();

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



    public DNSPayload resolveUpstream(DNSPayload payload, byte[] data) {
        throw new UnsupportedOperationException("Move queryResolver body here");
    }







    // Log helpers
    private void logCacheConnectionLost(long startTime) {
        logger.log(
                (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                        "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error", null).toHumanReadable()),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.ERROR
        );
    }

    private void logCacheInsertFailed(long startTime) {
        logger.log(
                (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                        "[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost", null).toHumanReadable()),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.ERROR
        );
    }
}