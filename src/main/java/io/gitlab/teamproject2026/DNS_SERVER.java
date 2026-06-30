package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.*;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.logging.*;
import io.gitlab.teamproject2026.server.*;
import io.gitlab.teamproject2026.resolver.*;
import io.gitlab.teamproject2026.zones.ZoneManager;

import java.net.*;
import java.util.*;
import java.sql.SQLException;

/**
 * Main entry point of the DNS server.
 * Coordinates configuration loading, zone management, caching,
 * query resolution, and UDP/TCP request handling.
 */
public class DNS_SERVER {
    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0";

    private static final int MAX_CNAME_HOPS = 8;

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String PINK = "\u001B[95m";

    private static CacheHandler cacheHandler;
    private static ZoneHandler zoneHandler;
    public static Config serverConfig;
    public static ResolverSection resolverOptions;
    private static StubResolver stubResolver;
    private static RecursiveResolver recursiveResolver;
    private static DNSResolutionPipeline resolutionPipeline;
    private static DNSResponseBuilder responseBuilder;
    public static Logger logger;
    private static List<String> zoneCatalog;

    private static int portNumber;
    private static String bindIP;
    private static InetAddress bindAddress;

    /*
    named.conf recap:
        recursion no                  -> never resolve, only answer local zones
        recursion yes + null          -> never forward, resolve everything yourself (plus answer local zones)
        recursion yes + FORWARD_FIRST -> Try forwarding first, then resolve everything yourself (plus answer local zones)
        recursion yes + FORWARD_ONLY  -> never resolve yourself, forward everything (plus answer local zones)
     */
    public static boolean recursionAllowed;
    private static ForwardMode forwardMode;
    // If true, disable glue data to improve performance
    private static boolean minimalResponses = false;

    private static long startTime;

    /**
     * Initializes the server and starts the UDP, TCP, and zone refresh workers.
     */
    static void main(String[] args) {
        initializeServer(args);
    }

    /**
     * Initializes the server state from configuration and command-line arguments.
     */
    private static void initializeServer(String[] args){
        IO.println("\n--------------------------------[ DNSSERVER ]--------------------------------- ");

        // named.conf
        serverConfig = ConfigManager.loadConfig("named.conf");
        logger = ConfigManager.logger; // A pointer to the actual logger
        logger.log(
                ("Starting DNS Server..."),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.NOTICE,
                true
        );

        OptionSection options = serverConfig.options();
        resolverOptions = options.resolverSection();

        // establish Database Connection
        initializeDatabase();


        // load zones into the database and store the zone names for easy lookup
        if(zoneHandler != null){
            zoneCatalog = ConfigManager.loadZonesFromConf(zoneHandler, serverConfig);
        } else {
            logger.log(
                    (RED + "[DATABASE] No zone handler found, skipping zones" + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            zoneCatalog = new ArrayList<>();
        }

        // Default options from the config
        portNumber = options.port();
        bindIP = options.ipAddress();

        applyCliArgs(args);

        // port check
        if (portNumber < 0 || portNumber > 65535) {
            logger.log(
                    (RED + "[SERVER] Invalid port: " + portNumber + " — port must be between 0 and 65535, using default "
                            + DEFAULT_PORT + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            portNumber = DEFAULT_PORT;
        }

        recursionAllowed = options.recursionAllowed();
        logger.log(
                (GREEN + "[SERVER] Recursion: " + recursionAllowed),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.INFO,
                true
        );
        forwardMode = options.forwardMode();// Determines if we should only forward
        if (forwardMode == null){
            logger.log(
                    (GREEN + "[RESOLVER] Configured ForwardMode is: NULL (don't forward)" ),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO,
                    true
            );
        }
        else {
            logger.log(
                    (GREEN + "[RESOLVER] Configured ForwardMode is:" + forwardMode),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO,
                    true
            );
        }

        List<NameServer> forwarders = options.forwarders().stream()
                .map(ip -> new NameServer(ip, 53, ip))
                .toList();
        stubResolver = new StubResolver( forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());
        recursiveResolver = new RecursiveResolver( cacheHandler, resolverOptions.timeout(), resolverOptions.retryDelay());
        responseBuilder = new DNSResponseBuilder(recursionAllowed);
        resolutionPipeline = new DNSResolutionPipeline(cacheHandler, zoneHandler, resolverOptions, stubResolver, recursiveResolver, logger, zoneCatalog, minimalResponses, forwardMode, startTime);
        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            logger.log(
                    (RED + "[SERVER] Invalid IP address: " + bindIP + " — using default " + DEFAULT_IP + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            bindIP = DEFAULT_IP;
            try {
                bindAddress = InetAddress.getByName(DEFAULT_IP);
            } catch (UnknownHostException ex) {
                throw new RuntimeException(ex);
            }
        }

        new Thread(DNS_SERVER::handleCacheCleanUp).start();
        new Thread(new UDPServer(cacheHandler, zoneHandler, resolutionPipeline, responseBuilder, bindAddress, portNumber, logger)).start();
        new Thread(new TCPServer(cacheHandler, zoneHandler, resolutionPipeline, responseBuilder, bindAddress, portNumber, logger)).start();
        new Thread(new ZoneManager(zoneHandler, serverConfig, zoneCatalog, startTime, logger)).start();
    }

    private static void applyCliArgs(String[] args) {
        // Overwrite default values
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                try {
                    portNumber = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.log(
                            (RED + "[SERVER] Invalid port: " + args[i + 1] + " — using default " + DEFAULT_PORT + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true
                    );
                    portNumber = DEFAULT_PORT;
                }
                i++;
            } else if (args[i].equals("-a") && i + 1 < args.length) {
                bindIP = args[i + 1];
                i++;
            } else if (args[i].equals("--less-log")) {
                if (logger != null){
                    logger.overwriteSeverity(LoggingSeverity.NOTICE);
                }
            } else if (args[i].equals("--quiet")) {
                if (logger != null){
                    logger.overwriteSeverity(LoggingSeverity.ERROR);
                }
            } else if (args[i].equals("--silent")) {
                if (logger != null){
                    logger.overwriteSeverity(LoggingSeverity.CRITICAL);
                }
            } else if (args[i].equals("--minimal-responses")) {
                minimalResponses = true;
            }
        }
    }

    private static void initializeDatabase() {
        startTime = System.currentTimeMillis();

        try {
            DatabaseConnectionProvider provider = new DatabaseConnectionProvider();
            cacheHandler = new CacheHandler(provider);
            zoneHandler = new ZoneHandler(provider);
            logger.log(
                    (GREEN + "[DATABASE] Connection to the database successful" + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.INFO,
                    true
            );
        } catch (ClassNotFoundException e) {
            logger.log(
                    (RED + "[DATABASE-ERROR] JDBC jar couldn't be located, perhaps the classpath isn't set up correctly?" + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.ERROR,
                    true
            );
        } catch (SQLException e) {
            logger.log(
                    (RED + "[DATABASE-ERROR] Connection to the database couldn't be established" + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.ERROR,
                    true
            );
            logCacheConnectionLost(startTime);
        }
    }

    // cache helper
    private static void handleCacheCleanUp() {
        while (!Thread.currentThread().isInterrupted()) {
            if (cacheHandler != null) {
                long cleanupStart = System.currentTimeMillis();
                try {
                    cacheHandler.deleteExpired();
                } catch (SQLException e) {
                    logger.log(
                            (RED + "[DATABASE-ERROR] Cache cleanup failed" + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true
                    );
                    logCacheConnectionLost(cleanupStart);
                }
            }
            try {
                Thread.sleep(10_000); // every 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static DNSPayload cacheLookUp(DNSPayload payload, DNSPacket oopPacket, String id){
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

    public static DNSPayload queryResolver(DNSPayload payload, byte[] data){
        // Build packet to be sent to Stub/recursive resolver
        // The ID should not have to be changed, as the Server should not receive any
        // other packages using this ID from the original sender
        DNSPacket uncachedOOPacket = DNSPacket.fromBytes(data);
        uncachedOOPacket.setQuestionSections(
                payload.notResolved().toArray(new QuestionSection[0]));
        uncachedOOPacket.setQdcount(payload.notResolved().size());

        // Get Stub/recursive response
        byte[] rawResponse=null;
        if (forwardMode == null) {
            try {
                logger.logTrace(PINK + "[TRACE] Resolve query recursively"+ RESET);
                rawResponse = recursiveResolver.resolve(uncachedOOPacket.toBytes());
            } catch (RuntimeException e) {
                logger.log(
                        (RED + "[RECURSIVE-ERROR] Failed to resolve query" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true
                );
                return payload;
            }
        }
        else {
            switch (forwardMode) {
                case FORWARD_ONLY -> { // only stub
                    logger.logTrace(PINK + "[TRACE] Forward query via Stubresolver"+ RESET);
                    try {
                        rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
                    } catch (RuntimeException e) {
                        logger.log(
                                (RED + "[STUB-ERROR] Failed to resolve query" + RESET),
                                LoggingCategoryType.QUERY_ERRORS,
                                LoggingSeverity.ERROR,
                                true
                        );
                        return payload;
                    }
                }
                case FORWARD_FIRST -> {
                    try {
                        // try stub
                        logger.logTrace(PINK + "[TRACE] Try forwarding query via Stubresolver"+ RESET);
                        rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
                    } catch (RuntimeException e) {
                        logger.log(
                            (YELLOW + "[SERVER] Forwarder failed or timed out in FORWARD_FIRST mode. Falling back to iterative resolution..." + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.WARNING,
                            true
                        );
                    }
                    // if stub resolver fails: fallback recursive resolver
                    if (rawResponse == null) {
                        try {
                            logger.logTrace(PINK + "[TRACE] Stubresolver unsuccessful, try recursive Resolver instead"+ RESET);
                            rawResponse = recursiveResolver.resolve(uncachedOOPacket.toBytes());
                        } catch (RuntimeException e) {
                            logger.log(
                                    (RED + "[RECURSIVE-ERROR] Iterative fallback failed" + RESET),
                                    LoggingCategoryType.QUERY_ERRORS,
                                    LoggingSeverity.ERROR,
                                    true
                            );
                            return payload;
                        }
                    }
                }
            }
        }


        DNSPacket parsedResponse = DNSPacket.fromBytes(rawResponse);
        //----------------------------ReferralAnalyzer-------------------------------------------------
        RRType queryType = payload.notResolved().isEmpty() ? RRType.A :
                payload.notResolved().getFirst().getQtype();
        //stub response
        ReferralAnalyzer.Classification classification=
                ReferralAnalyzer.classify(parsedResponse, queryType);
        logger.log(
                ("[ANALYZER]" + classification + queryType),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.INFO,
                true
        );
        switch (classification) {
            case ANSWER, CNAME_CHAIN, NODATA -> {
                // Extract RRs from Stub Response and cache them if cachHandler is available
                // caching
                logger.logTrace(PINK + "[TRACE] Query resolved successfully"+ RESET);
                if (cacheHandler != null) {
                    startTime = System.currentTimeMillis(); //set new startTime for logging of CACHE_ERROR
                    try {
                        cacheHandler.insertAll(parsedResponse.getAnswerSections());
                        cacheHandler.insertAll(parsedResponse.getAuthoritySections());
                        cacheHandler.insertAll(parsedResponse.getAdditionalSections());
                    } catch (SQLException e) {
                        logger.log(
                                ("[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost"),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR,
                                true
                        );
                        logCacheInsertFailed(startTime);
                    }
                }

                // extracting and adding to answers
                Collections.addAll(payload.answer(), parsedResponse.getAnswerSections());
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
                Collections.addAll(payload.additional(), parsedResponse.getAdditionalSections());
            }
            case NXDOMAIN -> {
                logger.logTrace(PINK + "[TRACE] NXDOMAIN received from upstream"+ RESET);
                logger.log(
                        (YELLOW + "[RESOLVER] NXDOMAIN received from upstream" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.WARNING,
                        true
                );
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
            }
            case REFERRAL -> {

                String referral = "";
                for (ResourceRecord ns : parsedResponse.getAuthoritySections()){
                    referral += String.join(".",((RDataNS)ns.getRdata()).getNsdname()) + ". , ";
                }
                logger.logTrace(PINK + "[TRACE] Referal to: " + referral + RESET);

                logger.log(
                        (YELLOW + "[RESOLVER] Referral received" + RESET),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.INFO,
                        true
                );
                Collections.addAll(payload.authority(), parsedResponse.getAuthoritySections());
                Collections.addAll(payload.additional(), parsedResponse.getAdditionalSections());
            }
            case SERVFAIL, FORMERR -> {
                logger.logTrace(PINK + "[TRACE] Resolving unsuccessful" + RESET);
                logger.log(
                        (RED + "[RESOLVER] upstream returned" + classification + ", skipping cache insert" + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true
                );
            }

        }



        return  payload;
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
    public static DNSPayload queryCache(DNSPacket query, CacheHandler cacheHandler) throws SQLException {

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


    // Log helpers
    private static void logCacheConnectionLost(long startTime) {
        logger.log(
                (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                        "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error", null).toHumanReadable()),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.ERROR
        );
    }

    private static void logCacheInsertFailed(long startTime) {
        logger.log(
                (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                        "[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost", null).toHumanReadable()),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.ERROR
        );
    }


}
