package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.NameServer;
import io.gitlab.teamproject2026.resolver.RecursiveResolver;
import io.gitlab.teamproject2026.resolver.StubResolver;
import io.gitlab.teamproject2026.server.DNSResolutionPipeline;
import io.gitlab.teamproject2026.server.DNSResponseBuilder;
import io.gitlab.teamproject2026.server.TCPServer;
import io.gitlab.teamproject2026.server.UDPServer;
import io.gitlab.teamproject2026.zones.ZoneManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point of the DNS server.
 * Coordinates configuration loading, zone management, caching,
 * query resolution, and UDP/TCP request handling.
 */
public class DNS_SERVER {
    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0";

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String PINK = "\u001B[95m";

    private static CacheHandler cacheHandler;
    private static ZoneHandler zoneHandler;
    public static Logger logger;

    private static int portNumber;
    private static String bindIP;

    /*
    named.conf recap:
        recursion no                  -> never resolve, only answer local zones
        recursion yes + null          -> never forward, resolve everything yourself (plus answer local zones)
        recursion yes + FORWARD_FIRST -> Try forwarding first, then resolve everything yourself (plus answer local zones)
        recursion yes + FORWARD_ONLY  -> never resolve yourself, forward everything (plus answer local zones)
     */
    public static boolean recursionAllowed;
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
        Config serverConfig = ConfigManager.loadConfig("named.conf");
        logger = ConfigManager.logger; // A pointer to the actual logger
        logger.log(
                ("Starting DNS Server..."),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.NOTICE,
                true
        );

        OptionSection options = serverConfig.options();
        ResolverSection resolverOptions = options.resolverSection();

        // establish Database Connection
        initializeDatabase();


        // load zones into the database and store the zone names for easy lookup
        List<String> zoneCatalog;
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
        ForwardMode forwardMode = options.forwardMode();// Determines if we should only forward
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
        StubResolver stubResolver = new StubResolver( forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());
        RecursiveResolver recursiveResolver = new RecursiveResolver( cacheHandler, resolverOptions.timeout(), resolverOptions.retryDelay());
        DNSResponseBuilder responseBuilder = new DNSResponseBuilder(recursionAllowed);
        DNSResolutionPipeline resolutionPipeline = new DNSResolutionPipeline(cacheHandler, zoneHandler, resolverOptions, stubResolver, recursiveResolver, logger, zoneCatalog, minimalResponses, forwardMode, startTime);
        InetAddress bindAddress;
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
                    assert logger != null;
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
            logger.log(
                    (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                            "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error", null).toHumanReadable()),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR
            );
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
                    logger.log(
                            (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,ResponseSource.CACHE, null, null, null, -1,
                                    "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error", null).toHumanReadable()),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR
                    );
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
}
