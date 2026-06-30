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
