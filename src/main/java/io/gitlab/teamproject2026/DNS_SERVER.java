package io.gitlab.teamproject2026;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.DashboardHandler;
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

/**
 * Main entry point of the DNS server. Coordinates configuration loading, zone management, caching, query resolution,
 * and UDP/TCP request handling.
 */
public class DNS_SERVER {
    private static ServerContext context;
    private static final List<Thread> serverThreads = new ArrayList<>();
    private static volatile boolean shuttingDown = false;
    private static UDPServer udpIPv4Server;
    private static UDPServer udpIPv6Server;
    private static TCPServer tcpIPv4Server;
    private static TCPServer tcpIPv6Server;

    /** Initializes the server and starts the UDP, TCP, and zone refresh workers. */
    static void main(String[] args) {
        initializeServer(args);
    }

    /** Initializes the server state from configuration and command-line arguments. */
    private static void initializeServer(String[] args) {
        IO.println("\n--------------------------------[ DNSSERVER ]--------------------------------- ");

        // named.conf
        Config serverConfig = ConfigManager.loadConfig("named.conf");
        Logger logger = ConfigManager.logger; // A pointer to the actual logger
        logger.log(("Starting DNS Server..."), LoggingCategoryType.GENERAL, LoggingSeverity.NOTICE);

        OptionSection options = serverConfig.options();
        ResolverSection resolverOptions = options.resolverSection();

        ServerCliOptions cliOptions =
                applyCliArgs(args, options.port(), options.ipv4Address(), options.ipv6Address(), logger);
        int portNumber = cliOptions.portNumber();
        String bindIPv4 = cliOptions.bindIPv4();
        String bindIPv6 = cliOptions.bindIPv6();
        boolean minimalResponses = cliOptions.minimalResponses();

        long startTime = System.currentTimeMillis();
        // establish Database Connection
        DatabaseHandlers dbHandlers = initializeDatabase(logger, startTime);
        ZoneHandler zoneHandler = dbHandlers.zoneHandler();
        CacheHandler cacheHandler = dbHandlers.cacheHandler();
        DashboardHandler dashboardHandler = dbHandlers.dashboardHandler();

        // load zones into the database and store the zone names for easy lookup
        List<String> zoneCatalog;
        if (zoneHandler != null) {
            zoneCatalog = ConfigManager.loadZonesFromConf(zoneHandler, serverConfig);
        } else {
            logger.log(
                    (RED + "[DATABASE] No zone handler found, skipping zones" + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true);
            zoneCatalog = new ArrayList<>();
        }

        // port check
        if (portNumber < 0 || portNumber > 65535) {
            logger.log(
                    (RED + "[SERVER] Invalid port: " + portNumber
                            + " — port must be between 0 and 65535, using default "
                            + OptionSection.defaultOptions().port() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true);
            portNumber = OptionSection.defaultOptions().port();
        }

        boolean dnssecEnable = options.dnssecEnable();
        logger.log(
                (GREEN + "[SERVER] Enable DNSSEC: " + dnssecEnable),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.INFO,
                true);
        boolean dnssecValidate = options.dnssecValidate();
        logger.log(
                (GREEN + "[SERVER] Validate DNSSEC: " + dnssecValidate),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.INFO,
                true);

        boolean recursionAllowed = options.recursionAllowed();
        logger.log(
                (GREEN + "[SERVER] Recursion: " + recursionAllowed),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.INFO,
                true);

        // Set and log the EDNS UDP size limit specified in the named.conf file
        int ednsUdpSize = options.ednsUdpSize();
        logger.log(
                (GREEN + "[SERVER] Server UDP size limit for EDNS: " + ednsUdpSize + " Bytes"),
                LoggingCategoryType.DEFAULT,
                LoggingSeverity.INFO,
                true);

        ForwardMode forwardMode = options.forwardMode(); // Determines if we should only forward
        if (forwardMode == null) {
            logger.log(
                    (GREEN + "[RESOLVER] Configured ForwardMode is: NULL (don't forward)"),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO,
                    true);
        } else {
            logger.log(
                    (GREEN + "[RESOLVER] Configured ForwardMode is:" + forwardMode),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.INFO,
                    true);
        }

        List<NameServer> forwarders = options.forwarders().stream()
                .map(ip -> new NameServer(ip, 53, ip))
                .toList();

        StubResolver stubResolver = new StubResolver(
                logger,
                forwarders,
                resolverOptions.timeout(),
                resolverOptions.maxRetries(),
                resolverOptions.retryDelay(),
                resolverOptions.strategy());
        RecursiveResolver recursiveResolver = new RecursiveResolver(
                cacheHandler, logger, resolverOptions.timeout(), resolverOptions.retryDelay(), ednsUdpSize);
        DNSResponseBuilder responseBuilder =
                new DNSResponseBuilder(ednsUdpSize, dnssecEnable, dnssecValidate, recursionAllowed);
        DNSResolutionPipeline resolutionPipeline = new DNSResolutionPipeline(
                cacheHandler,
                zoneHandler,
                resolverOptions,
                stubResolver,
                recursiveResolver,
                logger,
                zoneCatalog,
                minimalResponses,
                forwardMode,
                startTime,
                ednsUdpSize,
                dnssecEnable,
                dnssecValidate);

        InetAddress bindIPv4Address = null;
        if (bindIPv4 != null && !bindIPv4.equals("none")) {
            try {
                bindIPv4Address = InetAddress.getByName(bindIPv4);
            } catch (Exception e) {
                logger.log(
                        (RED + "[SERVER] Invalid IP address: " + bindIPv4 + RESET),
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.ERROR,
                        true);
            }
        }
        // Optional IPv6 server
        InetAddress bindIPv6Address = null;
        if (bindIPv6 != null && !bindIPv6.equals("none")) {
            IO.println("DEBUG" + bindIPv6);
            try {
                bindIPv6Address = InetAddress.getByName(bindIPv6);
            } catch (Exception e) {
                logger.log(
                        (RED + "[SERVER] Invalid IP address: " + bindIPv6 + RESET),
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.ERROR,
                        true);
            }
        }

        // Use the default IPv4 address if neither config, nor cli provided addresses
        if (bindIPv4Address == null && bindIPv6Address == null) {
            logger.log(
                    (YELLOW + "[SERVER] No IP address specified, using default + "
                            + OptionSection.defaultOptions().ipv4Address() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.WARNING,
                    true);
            try {
                bindIPv4Address =
                        InetAddress.getByName(OptionSection.defaultOptions().ipv4Address());
            } catch (UnknownHostException ex) {
                throw new RuntimeException(ex);
            }
        }

        context = new ServerContext(
                cacheHandler,
                zoneHandler,
                dashboardHandler,
                logger,
                resolutionPipeline,
                responseBuilder,
                serverConfig,
                zoneCatalog,
                ednsUdpSize,
                dnssecEnable,
                dnssecValidate,
                startTime);

        Runtime.getRuntime().addShutdownHook(new Thread(DNS_SERVER::shutdown, "shutdown-hook"));

        startServerThread(new Thread(DNS_SERVER::handleCacheCleanUp, "cache-cleanup"));
        if (bindIPv4Address != null) {
            udpIPv4Server = new UDPServer(context, bindIPv4Address, portNumber);
            tcpIPv4Server = new TCPServer(context, bindIPv4Address, portNumber);
            startServerThread(new Thread(udpIPv4Server, "udp-ipv4-server"));
            startServerThread(new Thread(tcpIPv4Server, "tcp-ipv4-server"));
        }
        if (bindIPv6Address != null) {
            udpIPv6Server = new UDPServer(context, bindIPv6Address, portNumber);
            tcpIPv6Server = new TCPServer(context, bindIPv6Address, portNumber);
            startServerThread(new Thread(udpIPv6Server, "udp-ipv6-server"));
            startServerThread(new Thread(tcpIPv6Server, "tcp-ipv6-server"));
        }
        startServerThread(new Thread(new ZoneManager(context), "zone-manager"));
    }
    // Stores the threads in a list directly
    private static void startServerThread(Thread thread) {
        serverThreads.add(thread);
        thread.start();
    }

    private record ServerCliOptions(int portNumber, String bindIPv4, String bindIPv6, boolean minimalResponses) {}

    private static ServerCliOptions applyCliArgs(
            String[] args, int portNumber, String bindIPv4, String bindIPv6, Logger logger) {
        boolean minimalResponses = false;
        // Overwrite default values
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                try {
                    portNumber = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    assert logger != null;
                    logger.log(
                            (RED + "[SERVER] Invalid port: " + args[i + 1] + " — using default "
                                    + OptionSection.defaultOptions().port() + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true);
                    portNumber = OptionSection.defaultOptions().port();
                }
                i++;
            } else if (args[i].equals("-a")
                    || args[i].equals("-4")
                    || args[i].equals("--ipv4")
                    || args[i].equals("--listen-on") && i + 1 < args.length) {
                bindIPv4 = args[i + 1];
                i++;
            } else if (args[i].equals("-6")
                    || args[i].equals("--ipv6")
                    || args[i].equals("--listen-on-6") && i + 1 < args.length) {
                bindIPv6 = args[i + 1];
                i++;
            } else if (args[i].equals("--verbose") || args[i].equals("-v")) {
                if (logger != null) {
                    logger.overwriteSeverity(LoggingSeverity.DEBUG);
                }
            } else if (args[i].equals("--quiet") || args[i].equals("-q")) {
                if (logger != null) {
                    logger.overwriteSeverity(LoggingSeverity.ERROR);
                }
            } else if (args[i].equals("--silent")) {
                if (logger != null) {
                    logger.overwriteSeverity(LoggingSeverity.CRITICAL);
                }
            } else if (args[i].equals("--log-level")) {
                if (i + 1 >= args.length) {
                    // error: --log-level requires a value
                } else {
                    logger.overwriteSeverity(LoggingSeverity.fromValue(args[++i]));
                }
            } else if (args[i].equals("--minimal-responses")) {
                minimalResponses = true;
            }
        }
        return new ServerCliOptions(portNumber, bindIPv4, bindIPv6, minimalResponses);
    }

    private record DatabaseHandlers(
            CacheHandler cacheHandler, ZoneHandler zoneHandler, DashboardHandler dashboardHandler) {}

    private static DatabaseHandlers initializeDatabase(Logger logger, long startTime) {
        try {
            DatabaseConnectionProvider provider = new DatabaseConnectionProvider();
            logger.log(
                    (GREEN + "[DATABASE] Connection to the database successful" + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.INFO,
                    true);
            return new DatabaseHandlers(
                    new CacheHandler(provider), new ZoneHandler(provider), new DashboardHandler(provider));
        } catch (ClassNotFoundException e) {
            logger.log(
                    (RED
                            + "[DATABASE-ERROR] JDBC jar couldn't be located, perhaps the classpath isn't set up correctly?"
                            + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.ERROR,
                    true);
        } catch (SQLException e) {
            logger.log(
                    (RED + "[DATABASE-ERROR] Connection to the database couldn't be established" + RESET),
                    LoggingCategoryType.GENERAL,
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
                                    "[SERVER-ERROR] Connection to cache Server has been lost, assume internal Server error",
                                    -1)
                            .toHumanReadable()),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR);
        }
        return new DatabaseHandlers(null, null, null);
    }

    // cache helper
    private static void handleCacheCleanUp() {
        while (!Thread.currentThread().isInterrupted()) {
            long cleanUpStarTime = System.currentTimeMillis();
            CacheHandler cacheHandler = context.cacheHandler();
            if (cacheHandler != null) {
                try {
                    cacheHandler.deleteExpired();
                } catch (SQLException e) {
                    context.logger()
                            .log(
                                    (RED + "[DATABASE-ERROR] Cache cleanup failed" + RESET),
                                    LoggingCategoryType.DEFAULT,
                                    LoggingSeverity.ERROR,
                                    true);
                    context.logger()
                            .log(
                                    (DNSEvent.event(
                                                    DNSEventType.CACHE_ERROR,
                                                    null,
                                                    null,
                                                    -1,
                                                    System.currentTimeMillis() - cleanUpStarTime,
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

    public static void shutdown() {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        if (context != null && context.logger() != null) {
            context.logger()
                    .log(
                            (YELLOW + "[SERVER] Shutdown requested. Stopping DNS server..." + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true);
        } else {
            IO.println(YELLOW + "[SERVER] Shutdown requested. Stopping DNS server..." + RESET);
        }
        if (udpIPv4Server != null) {
            udpIPv4Server.shutdown();
        }
        if (udpIPv6Server != null) {
            udpIPv6Server.shutdown();
        }
        if (tcpIPv4Server != null) {
            tcpIPv4Server.shutdown();
        }
        if (tcpIPv6Server != null) {
            tcpIPv6Server.shutdown();
        }

        Thread currentThread = Thread.currentThread();
        for (Thread thread : serverThreads) {
            if (thread != currentThread) {
                thread.interrupt();
            }
        }
        for (Thread thread : serverThreads) {
            if (thread != currentThread) {
                try {
                    thread.join(2_000);
                } catch (InterruptedException e) {
                    currentThread.interrupt();
                    break;
                }
            }
        }
        if (context != null && context.cacheHandler() != null) {
            context.cacheHandler().close();
        }

        if (context != null && context.logger() != null) {
            context.logger()
                    .log(
                            (GREEN + "[SERVER] DNS server stopped." + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true);
            context.logger().close();
        } else {
            IO.println(GREEN + "[SERVER] DNS server stopped." + RESET);
        }
    }
}
