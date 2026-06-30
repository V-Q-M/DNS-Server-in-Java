package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.RecursiveResolver;
import io.gitlab.teamproject2026.resolver.StubResolver;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;

import java.sql.SQLException;
import java.util.Comparator;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
        throw new UnsupportedOperationException("Move cacheLookUp body here");
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