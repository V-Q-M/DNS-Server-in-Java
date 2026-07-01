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
import io.gitlab.teamproject2026.server.*;
import io.gitlab.teamproject2026.zones.ZoneManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.gitlab.teamproject2026.AnsiColors.*;

/**
 * Main entry point of the DNS server.
 * Coordinates configuration loading, zone management, caching,
 * query resolution, and UDP/TCP request handling.
 */
public class DNS_SERVER {
    //private static final String DEFAULT_IP = "0.0.0.0";
    private static ServerContext context;
    //public static Logger logger;

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
        Logger logger = ConfigManager.logger; // A pointer to the actual logger
        logger.log(
                ("Starting DNS Server..."),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.NOTICE,
                true
        );

        OptionSection options = serverConfig.options();
        ResolverSection resolverOptions = options.resolverSection();

        // establish Database Connection
        initializeDatabase(logger);
        ZoneHandler zoneHandler = context.zoneHandler();
        CacheHandler cacheHandler = context.cacheHandler();
        long startTime = context.startTime();

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

        ServerCliOptions cliOptions = applyCliArgs(
                args,
                options.port(),
                options.ipAddress(),
                logger
        );

        int portNumber = cliOptions.portNumber();
        String bindIP = cliOptions.bindIP();
        boolean minimalResponses = cliOptions.minimalResponses();

        // port check
        if (portNumber < 0 || portNumber > 65535) {
            logger.log(
                    (RED + "[SERVER] Invalid port: " + portNumber + " — port must be between 0 and 65535, using default "
                            + OptionSection.defaultOptions().port() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            portNumber = OptionSection.defaultOptions().port();
        }

        boolean recursionAllowed = options.recursionAllowed();
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

        StubResolver stubResolver = new StubResolver(logger, forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());
        RecursiveResolver recursiveResolver = new RecursiveResolver(cacheHandler, logger, resolverOptions.timeout(), resolverOptions.retryDelay());
        DNSResponseBuilder responseBuilder = new DNSResponseBuilder(recursionAllowed);
        DNSResolutionPipeline resolutionPipeline = new DNSResolutionPipeline(cacheHandler, zoneHandler, resolverOptions, stubResolver, recursiveResolver, logger, zoneCatalog, minimalResponses, forwardMode, startTime);

        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            logger.log(
                    (RED + "[SERVER] Invalid IP address: " + bindIP + " — using default " + OptionSection.defaultOptions().ipAddress() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
            try {
                bindAddress = InetAddress.getByName(OptionSection.defaultOptions().ipAddress());
            } catch (UnknownHostException ex) {
                throw new RuntimeException(ex);
            }
        }
        context = new ServerContext(cacheHandler,
                zoneHandler,
                logger,
                resolutionPipeline,
                responseBuilder,
                serverConfig,
                zoneCatalog,
                portNumber,
                bindAddress,
                startTime
        );

        new Thread(DNS_SERVER::handleCacheCleanUp).start();
        new Thread(new UDPServer(context), "udp-server").start();
        new Thread(new TCPServer(context), "tcp-server").start();
        new Thread(new ZoneManager(context)).start();
    }

    private record ServerCliOptions(
            int portNumber,
            String bindIP,
            boolean minimalResponses
    ) {}
    private static ServerCliOptions applyCliArgs(String[] args, int portNumber, String bindIP, Logger logger) {
        boolean minimalResponses = false;
        // Overwrite default values
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                try {
                    portNumber = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    assert logger != null;
                    logger.log(
                            (RED + "[SERVER] Invalid port: " + args[i + 1] + " — using default " + OptionSection.defaultOptions().port() + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true
                    );
                    portNumber = OptionSection.defaultOptions().port();
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
        return new ServerCliOptions(portNumber, bindIP, minimalResponses);
    }

    private static void initializeDatabase(Logger logger) {
        long startTime = System.currentTimeMillis();

        try {
            DatabaseConnectionProvider provider = new DatabaseConnectionProvider();
            context = new ServerContext(new CacheHandler(provider), new ZoneHandler(provider), logger, startTime);
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
        long cleanUpStartime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            CacheHandler cacheHandler = context.cacheHandler();
            if (cacheHandler != null) {
                try {
                    cacheHandler.deleteExpired();
                } catch (SQLException e) {
                    context.logger().log(
                            (RED + "[DATABASE-ERROR] Cache cleanup failed" + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true
                    );
                    context.logger().log(
                            (DNSEvent.event(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - cleanUpStartime,ResponseSource.CACHE, null, null, null, -1,
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
