package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.options.*;
import io.gitlab.teamproject2026.config.zones.*;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.logging.*;
import io.gitlab.teamproject2026.model.rdata.RDataSOA;
import io.gitlab.teamproject2026.resolver.*;

import java.io.*;
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

    private static final int UDP_MAX_PAYLOAD_SIZE = 65507; // see https://en.wikipedia.org/wiki/User_Datagram_Protocol
    private static final int MAX_CNAME_HOPS = 8;

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";

    // probably obsolete?
    private static boolean testing = false;


    // If true, disable glue data to improve performance
    private static boolean minimalResponses = false;
    // If true, disable a few success messages in the server to improve performance
    private static boolean lessLog = false;


    private static Config serverConfig;
    private static ResolverSection resolverOptions;
    private static List<String> zoneCatalog;
    private static StubResolver stubResolver;
    private static CacheHandler cacheHandler;
    private static ZoneHandler zoneHandler;

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
    private static boolean recursionAllowed;
    private static ForwardMode forwardMode;

    private static long startTime;

    /**
     * Intermediate result used while resolving a DNS query, separating
     * resolved records from questions that still require further processing.
     */
    public record DNSPayload(
            LinkedList<ResourceRecord> answer,
            LinkedList<ResourceRecord> authority,
            LinkedList<ResourceRecord> additional,
            LinkedList<QuestionSection> notResolved) {
    }

    /**
     * Initializes the server and starts the UDP, TCP, and zone refresh workers.
     */
    static void main(String[] args) {
        initializeServer(args);
        new Thread(DNS_SERVER::handleCacheCleanUp).start();
        new Thread(DNS_SERVER::handleUDP).start();
        new Thread(DNS_SERVER::handleTCP).start();
        new Thread(DNS_SERVER::handleZoneRefresh).start();
    }

    // Check if it's time to refresh the zones and perform the refresh if needed
    private static void handleZoneRefresh() {
        OptionSection options = serverConfig.options();
        String dir = options.dir();
        Map<String, Long> lastRefreshZoneBy = new HashMap<>();

        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            for (String zone : zoneCatalog) {
                try {
                    RDataSOA zoneSoa = zoneHandler.lookupZoneSoa(zone);
                    long refreshMillis = zoneSoa.getRefresh() * 1000L;
                    long lastRefresh = lastRefreshZoneBy.getOrDefault(zone, startTime);

                    //IO.println("Zone: " + zone + " " + (now - lastRefresh + ":" + refreshMillis));
                    if (now - lastRefresh < refreshMillis) {
                        continue;
                    }
                    if (!lessLog) {
                        IO.println(BLUE + "[ZONE] Starting zone refresh for \"" + zone + "\"" + RESET);
                    }

                    lastRefreshZoneBy.put(zone, now);

                    String file = findZoneFile(zone);
                    if (file == null) {
                        continue;
                    }

                    DNSZone newLoadedZone = ConfigManager.loadZoneFile(dir, file, zone);

                    if (newLoadedZone != null) {
                        if (!lessLog) {
                            IO.println("[ZONE] Comparing local serial: " + newLoadedZone.serial() + " to cached serial: " + zoneSoa.getSerial());
                        }
                        if (newLoadedZone.serial() != zoneSoa.getSerial()) {
                            IO.println(YELLOW + "[ZONE] File: \"" + file + "\" changed, reloading zone..." + RESET);
                            zoneHandler.replaceZone(newLoadedZone);
                        } else {
                            if (!lessLog) {
                                IO.println(GREEN + "[ZONE] Serials match -> Nothing to do" + RESET);
                            }
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (SQLException e) {
                    System.err.println(RED + "[DATABASE-ERROR] Couldn't query SOA record of: \"" + zone + "\"" + RESET);
                }
            }
        }
    }

    private static String findZoneFile(String origin){
        for (ZoneSection zoneSection : serverConfig.zones()){
            if (zoneSection.name().equalsIgnoreCase(origin)){
                return zoneSection.file();
            }
        }
        return null;
    }

    private static void handleUDP(){
        // Open UDP socket
        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println(GREEN + "[SERVER] UDP server listening on " + bindIP + ":" + portNumber + RESET);

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                if (!lessLog) {
                    IO.println(BLUE + "[SERVER] Received UDP packet from " + clientAddress + ":" + clientPort + RESET);
                    // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                    IO.println("[SERVER] IN: " + toHex(data));
                }

                // Handle the query and receive a byte array as response
                byte[] byteResponse = handleUDPQuery(data);

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        byteResponse,
                        byteResponse.length,
                        clientAddress,
                        clientPort);

                // Actually send the packet over our socket
                socket.send(responsePacket);

                if (!lessLog) {
                    IO.println(BLUE + "[SERVER] Send UDP response to: " + clientAddress + ":" + clientPort + RESET);
                    IO.println("[SERVER] OUT: " + toHex(byteResponse));
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private static void handleTCP(){
        // Open TCP socket
        try (ServerSocket serverSocket = new ServerSocket(portNumber, 50, bindAddress)) {
            IO.println(GREEN + "[SERVER] TCP server listening on " + bindIP + ":" + portNumber + RESET);

            // Server loop
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));


                    InetAddress clientAddress = socket.getInetAddress();
                    int clientPort = socket.getPort();

                    int length = input.readUnsignedShort();
                    byte[] data = input.readNBytes(length);

                    if (data.length != length) {
                        System.err.println(RED + "[SERVER-ERROR] Incomplete DNS query" + RESET);
                        continue;
                    }
                    if (!lessLog) {
                        IO.println(BLUE + "[SERVER] Received TCP packet from " + clientAddress + ":" + clientPort + RESET);

                        IO.println("[SERVER] IN: " + toHex(data));
                    }

                    byte[] response = handleQuery(data);

                    if (response.length > 65535) {
                        System.err.println(RED + "[SERVER-ERROR] TCP DNS response too large" + RESET);
                        continue;
                    }

                    output.writeShort(response.length);
                    output.write(response);
                    output.flush();

                    if (!lessLog) {
                        IO.println(BLUE + "[SERVER] Sent TCP response to " + clientAddress + ":" + clientPort + RESET);
                        IO.println("[SERVER] OUT: " + toHex(response));
                    }


                } catch (IOException e) {
                    System.err.println(RED + "[SERVER-ERROR] Failed to handle TCP client" + RESET);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }




    /**
     * Initializes the server state from configuration and command-line arguments.
     */
    private static void initializeServer(String[] args){
        IO.println("\n--------------------------------[ DNSSERVER ]--------------------------------- ");

        // named.conf
        serverConfig = ConfigManager.loadConfig("named.conf");

        OptionSection options = serverConfig.options();
        resolverOptions = options.resolverSection();

        // establish Database Connection
        initializeDatabase();

        // load zones into the database and store the zone names for easy lookup
        if(zoneHandler != null){
            zoneCatalog = loadZonesFromConf(serverConfig);
        } else {
            System.err.println(RED + "[DATABASE] No zone handler found, skipping zones" + RESET);
            zoneCatalog = new ArrayList<>();
        }

        // Default options from the config
        portNumber = options.port();
        bindIP = options.ipAddress();

        applyCliArgs(args);

        // port check
        if (portNumber < 0 || portNumber > 65535) {
            System.err.println(RED + "[SERVER] Invalid port: " + portNumber + " — port must be between 0 and 65535, using default "
                    + DEFAULT_PORT + RESET);
            portNumber = DEFAULT_PORT;
        }

        recursionAllowed = options.recursionAllowed();
        //IO.println("Recursion: " + recursionAllowed);
        forwardMode = options.forwardMode();// Determines if we should only forward
        //IO.println("Forward mode: " + forwardMode);

        List<Resolver> forwarders = options.forwarders().stream()
                .map(ip -> new Resolver(ip, 53, ip))
                .toList();
        stubResolver = new StubResolver(
                forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());

        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            System.err.println(RED + "[SERVER] Invalid IP address: " + bindIP + " — using default " + DEFAULT_IP + RESET);
            bindIP = DEFAULT_IP;
            try {
                bindAddress = InetAddress.getByName(DEFAULT_IP);
            } catch (UnknownHostException ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    private static void applyCliArgs(String[] args) {
        // Overwrite default values
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                try {
                    portNumber = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println(RED + "Invalid port: " + args[i + 1] + " — using default " + DEFAULT_PORT + RESET);
                    portNumber = DEFAULT_PORT;
                }
                i++;
            } else if (args[i].equals("-a") && i + 1 < args.length) {
                bindIP = args[i + 1];
                i++;
            } else if (args[i].equals("--testing")) {
                testing = true;
            } else if (args[i].equals("--less-log")) {
                lessLog = true;
            } else if (args[i].equals("--minimal-responses")) {
                minimalResponses = true;
            }
        }
    }

    private static void initializeDatabase() {
        startTime = System.currentTimeMillis();

        if (testing) return;

        try {
            DatabaseConnectionProvider provider = new DatabaseConnectionProvider();
            cacheHandler = new CacheHandler(provider);
            zoneHandler = new ZoneHandler(provider);
            System.err.println(GREEN + "[DATABASE] Connection to the database successful" + RESET);
        } catch (ClassNotFoundException e) {
            System.err.println(RED + "[DATABASE-ERROR] JDBC jar couldn't be located, perhaps the classpath isn't set up correctly?" + RESET);
            testing = true;
        } catch (SQLException e) {
            System.err.println(RED + "[DATABASE-ERROR] Connection to the database couldn't be established" + RESET);
            logCacheConnectionLost(startTime);
            testing = true;
        }
    }

    // cache helper
    private static void handleCacheCleanUp() {
        while (!Thread.currentThread().isInterrupted()) {
            if (!testing && cacheHandler != null) {
                long cleanupStart = System.currentTimeMillis();
                try {
                    cacheHandler.deleteExpired();
                } catch (SQLException e) {
                    IO.println(RED + "[DATABASE-ERROR] Cache cleanup failed" + RESET);
                    logCacheConnectionLost(cleanupStart);
                    testing = true;
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

    /**
     * Handles an incoming DNS query and produces the corresponding response.
     */
    private static byte[] handleQuery(byte[] data){
        // Map byteData to oop classes
        DNSPacket oopPacket = DNSPacket.fromBytes(data);

        // Uncomment this if you need to debug outgoing packets
        //IO.println("[SERVER] Assembled Packet:\n" + oopPacket);

        // Respond: If requested RR is cached, return RR else return stub Response

        DNSPayload payload = new DNSPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                new LinkedList<>());

        //needed for cache logging:
        byte[] idBytes = oopPacket.getId();
        String id = "0x" + HexFormat.of().formatHex(idBytes);
        boolean authoritative = false;
        if (!testing && cacheHandler != null && zoneHandler != null) {
            // Step 1: Check authoritative zones first (Zone -> Cache -> Forward)
            payload = zoneLookUp(oopPacket, id);

            // If recursion is disabled and something is outside our zones, REFUSE
            if (!recursionAllowed && !payload.notResolved.isEmpty()) {
                System.err.println(RED + "[SERVER] Received a foreign query but recursion is currently not allowed!");
                DNSPacket response = buildResponsePacket(payload, oopPacket, false);
                response.setRd(oopPacket.isRd());
                response.setRa(false);
                response.setRcode(RCode.REFUSED);

                // keep only authoritative data we already found, if any
                response.setAnswerSections(payload.answer.toArray(new ResourceRecord[0]));
                response.setAuthoritySections(payload.authority.toArray(new ResourceRecord[0]));
                response.setAdditionalSections(payload.additional.toArray(new ResourceRecord[0]));

                return serializeResponsePacket(response);
            }


            // If all questions matched an authoritative zone but no records exist -> NXDOMAIN
            // True NXDOMAIN only when ALL sections are empty:
            // authority may contain NS records from an authoritative zone answer
            if (payload.notResolved.isEmpty() && payload.answer.isEmpty() && payload.authority.isEmpty()) {

                DNSPacket response = new DNSPacket();
                response.setId(oopPacket.getId());
                response.setOpcode(oopPacket.getOpcode());
                response.setAa(true);
                response.setRd(oopPacket.isRd());
                response.setRa(recursionAllowed);
                response.setRcode(RCode.NXDOMAIN);
                response.setQuestionSections(oopPacket.getQuestionSections());
                response.setAnswerSections(new ResourceRecord[0]);
                response.setAuthoritySections(new ResourceRecord[0]);
                response.setAdditionalSections(new ResourceRecord[0]);
                return serializeResponsePacket(response);
            }
            // Step 2: Cache lookup only for questions not resolved by zone
            if(payload.notResolved.isEmpty()){
                authoritative = true;
            }
            payload = cacheLookUp(payload, oopPacket, id);
        } else {
            // if testing is active, do not check cache
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                payload.notResolved.add(request);
                if (!lessLog) {
                    LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L, ResponseSource.CACHE)); //since cache isn't checked latency is 0
                    LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L, ResponseSource.CACHE,
                            resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "testing is active, do not check cache", null));
                }

            }
        }

        // StubResolver should only be queried if there are queries not answered by the
        // cache and recursion is allowed
        if (recursionAllowed && !payload.notResolved.isEmpty()) {
            // if (testing){
            payload = queryStubResolver(payload, data);
        }

        return serializeResponsePacket(buildResponsePacket(payload, oopPacket, authoritative));
    }


    /*
    Specialized version of handleQuery which also checks for truncated response
     */
    private static byte[] handleUDPQuery(byte[] data) {
        byte[] byteResponse = handleQuery(data);

        if (byteResponse.length <= 512) {
            return byteResponse;
        }

        DNSPacket query = DNSPacket.fromBytes(data);
        DNSPacket response = DNSPacket.fromBytes(byteResponse);

        // For now: return only header + original question.
        // Client should retry over TCP.
        response.setTc(true);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        response.setQuestionSections(query.getQuestionSections());

        return response.toBytes();
    }



    private static byte[] serializeResponsePacket(DNSPacket response) {

        // convert response packet to Bytes
        byte[] byteResponse = response.toBytes();

        /* obsolete?

        // if DNS-Packet exceeds maximum UDP Payload size, truncate it and set the tc
        // flag
        if (byteResponse.length >= UDP_MAX_PAYLOAD_SIZE) {
            byte[] byteResponseTruncated = new byte[UDP_MAX_PAYLOAD_SIZE];
            response.setTc(true);
            byte[] truncatedSource = response.toBytes();
            System.arraycopy(truncatedSource, 0, byteResponseTruncated, 0, UDP_MAX_PAYLOAD_SIZE);
            byteResponse = byteResponseTruncated;
        }

         */
        return byteResponse;
    }
    private static DNSPayload zoneLookUp(DNSPacket oopPacket, String id) {
        DNSPayload payload = new DNSPayload(
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>()
        );
        for (QuestionSection questionSection : oopPacket.getQuestionSections()) {
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
                    payload.notResolved.add(fwdQuestion);
                    break;
                }

                // Step 3: we are authoritative for this domain
                try {
                    List<ResourceRecord> directMatches = zoneHandler.lookup(currentName, questionSection.getQtype(), zone);

                    if (!directMatches.isEmpty()) {
                        // Step 3a: records found -> route to correct section per RFC1034 4.3.2
                        if (!lessLog) {
                            LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.SUCCESS, id, currentName, questionSection.getQtype(), 0L, ResponseSource.ZONE));
                            LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.SUCCESS, id, currentName, questionSection.getQtype(), 0L, ResponseSource.ZONE,
                                    resolverOptions.strategy().toString(), zone, "Local Zone", 1, null, null));
                        }
                        for (ResourceRecord record : directMatches) {
                            switch (record.getType()) {
                                case NS -> {
                                    // NS records go to authority section, consistent with queryCache
                                    payload.authority.add(record);
                                    // Step 6: add glue A/AAAA for the nameserver to additional
                                    if (!minimalResponses) {
                                        String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                        try {
                                            payload.additional.addAll(zoneHandler.lookup(nsHost, RRType.A, zone));
                                            payload.additional.addAll(zoneHandler.lookup(nsHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) { /* glue records are best-effort */ }
                                    }
                                }
                                case MX -> {
                                    // MX records go to answer section
                                    payload.answer.add(record);
                                    // Step 6: add A/AAAA of mail exchange to additional
                                    if (!minimalResponses) {
                                        String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                        try {
                                            payload.additional.addAll(zoneHandler.lookup(mxHost, RRType.A, zone));
                                            payload.additional.addAll(zoneHandler.lookup(mxHost, RRType.AAAA, zone));
                                        } catch (SQLException ignored) { /* additional records are best-effort */ }
                                    }
                                }
                                // A, AAAA, CNAME, TXT, SOA, PTR -> directly to answer
                                default -> payload.answer.add(record);
                            }
                        }
                        IO.println(GREEN + "[ZONE] Success! Found: \"" + currentName + "\" in zone: \"" + zone + "\"" + RESET);
                        break; // records found -> done with this question
                    }

                    // No direct match -> check for CNAME (skip if QTYPE=CNAME, that is already answered above)
                    if (questionSection.getQtype() != RRType.CNAME) {
                        List<ResourceRecord> cnameMatches = zoneHandler.lookup(currentName, RRType.CNAME, zone);

                        if (!cnameMatches.isEmpty()) {
                            // Per RFC1034 4.3.2 step 3a: copy CNAME to answer,
                            // then go back to step 1 with the new QNAME (= restart the while loop)
                            payload.answer.addAll(cnameMatches);
                            currentName = String.join(".",
                                    ((RDataCNAME) cnameMatches.getFirst().getRdata()).getCname()).toLowerCase();
                            continue; //  restart = "go back to step 1" per RFC
                        }
                    }

                    // No record and no CNAME -> NXDOMAIN.
                    // Question intentionally not added to notResolved; the caller detects
                    // NXDOMAIN via (notResolved.isEmpty && answer.isEmpty && authority.isEmpty).
                    IO.println(YELLOW + "[ZONE] Warning! Zone: \"" + zone + "\" is responsible for: \"" + currentName + "\" but has no entry for it" + RESET);
                    break;

                } catch (SQLException e) {
                    IO.println(RED + "[DATABASE-ERROR] Connection to database Server has been lost, assume internal Server error" + RESET);
                    payload.notResolved.add(questionSection); // fall back to cache/forward
                    logCacheConnectionLost(startTime);
                    break;
                }
            }
            // If loop exits because visited.add(currentName) = false -> CNAME loop detected.
            // CNAMEs found so far remain in answer; stub resolver will handle the rest.
        }
        return payload;
    }
    private static DNSPayload cacheLookUp(DNSPayload payload, DNSPacket oopPacket, String id){
        // query cache and safe results in payload
        long cacheStart = System.currentTimeMillis();

        // Only query cache for questions not already answered by zone lokup
        // This preserves zone answers and prevents forwardigns authoritative domains
        DNSPacket unresolvedPacket = new DNSPacket();
        unresolvedPacket.setQuestionSections(payload.notResolved.toArray(new QuestionSection[0]));

        try {
            DNSPayload cacheResult = queryCache(unresolvedPacket, cacheHandler);
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                String domain = request.nameToString();
                RRType qType = request.getQtype();
                if (cacheResult.notResolved.contains(request)) {
                    IO.println(BLUE + "[CACHE] Miss! Couldn't find: " + qType + " of \"" + domain + "\"" + RESET);
                    if (!lessLog) {
                        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, domain, qType, System.currentTimeMillis() - cacheStart, ResponseSource.CACHE));
                        LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, domain, qType, 0L, ResponseSource.CACHE, resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "Not found in database", null));
                    }

                } else {
                    IO.println(GREEN + "[CACHE] Hit! Found: " + qType + " Record of " + domain + RESET);
                    if (!lessLog) {
                        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_HIT, id, domain, qType, System.currentTimeMillis() - cacheStart, ResponseSource.CACHE));
                        LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_HIT, id, domain, qType, 0L, ResponseSource.CACHE,
                                resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, null, null));
                    }
                }

            }
            // Merge cache results with existing zone answers
            payload.answer.addAll(cacheResult.answer);
            payload.authority.addAll(cacheResult.authority);
            payload.additional.addAll(cacheResult.additional);
            return new DNSPayload(payload.answer, payload.authority, payload.additional, cacheResult.notResolved);

        } catch (SQLException e) {
            // log
            LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L,ResponseSource.CACHE)); //no query, so everything except DNSEventType null

            LoggingFeature.logDebug(DNSEventDebug.debug(
                    DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - cacheStart, ResponseSource.CACHE,
                    null, null, null, -1,
                    "Connection to cache Server has been lost, assume internal Server error",
                    null));//debug fields.

            // payload.notResolved already has the unresolved questions from zone lookup,
            // no need to add them again – just log them as misses for the stub resolver
            for (QuestionSection request : unresolvedPacket.getQuestionSections()) {
                LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), System.currentTimeMillis() - cacheStart,ResponseSource.CACHE));
                LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), System.currentTimeMillis() - cacheStart, ResponseSource.CACHE,
                        resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "cache couldn't be reached", null));
            }
        }
        return payload;
    }

    private static DNSPayload queryStubResolver(DNSPayload payload, byte[] data){
        // Build packet to be sent to Stub resolver
        // The ID should not have to be changed, as the Server should not receive any
        // other packages using this ID from the original sender
        DNSPacket uncachedOOPacket = DNSPacket.fromBytes(data);
        uncachedOOPacket.setQuestionSections(
                payload.notResolved.toArray(new QuestionSection[0]));
        uncachedOOPacket.setQdcount(payload.notResolved.size());

        // Get Stub response
        byte[] rawResponse;
        try {
            rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
        } catch (RuntimeException e) {
            System.err.println(RED + "[STUB-ERROR] Failed to resolve query" + RESET);
            return payload;
        }
        DNSPacket stubResponse = DNSPacket.fromBytes(rawResponse);

        // Extract RRs from Stub Response and cache them if testing mode is not enabled
        // caching
        if (!testing && cacheHandler != null) {
            startTime = System.currentTimeMillis(); //set new startTime for logging of CACHE_ERROR
            try {
                cacheHandler.insertAll(stubResponse.getAnswerSections());
                cacheHandler.insertAll(stubResponse.getAuthoritySections());
                cacheHandler.insertAll(stubResponse.getAdditionalSections());
            } catch (SQLException e) {
                System.err.println("[SERVER-ERROR] Entries couldn't be inserted into Cache. Assume cache connection is lost");
                logCacheInsertFailed(startTime);
            }
        }

        // extracting and adding to answers
        Collections.addAll(payload.answer, stubResponse.getAnswerSections());
        Collections.addAll(payload.authority, stubResponse.getAuthoritySections());
        Collections.addAll(payload.additional, stubResponse.getAdditionalSections());

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
            System.err.println(
                    "Connection to Server couldn't be established. Assume internal Server error. Is the Postgres-Docker running?");
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


    /**
     * Build a response packet given a Payload for a given query
     *
     * @param payload The Payload that is to be put into the response packet
     * @param query   The Query that is to be answered
     * @return a DNS-Packet in oop format
     */
    private static DNSPacket buildResponsePacket(DNSPayload payload, DNSPacket query, boolean authoritative) {
        DNSPacket response = new DNSPacket();
        response.setId(query.getId());
        response.setOpcode(query.getOpcode());

        response.setAa(authoritative); // AA=true only for answers served from our own zone
        response.setRd(query.isRd());
        response.setRa(recursionAllowed);
        response.setRcode(RCode.NOERROR);
        response.setQuestionSections(query.getQuestionSections());
        response.setAnswerSections(payload.answer.toArray(new ResourceRecord[0]));
        response.setAuthoritySections(payload.authority.toArray(new ResourceRecord[0]));
        response.setAdditionalSections(payload.additional.toArray(new ResourceRecord[0]));
        return response;
    }



    // Config helper
    private static List<String> loadZonesFromConf(Config config) {
        OptionSection options = config.options();
        String dir = options.dir();
        List<ZoneSection> declaredZones = config.zones();

        List<String> zoneCatalog = new ArrayList<>();
        try{
            zoneHandler.clearAll();
            for (ZoneSection zone : declaredZones) {
                // Collect all zones specified in the config
                DNSZone loadedZone = ConfigManager.loadZoneFile(dir, zone.file(), zone.name());
                if (loadedZone != null) {
                    zoneHandler.insertZone(loadedZone);
                    zoneCatalog.add(zone.name());
                }
            }
        } catch (Exception e) {
            System.err.println(RED + "Failed to insert zone files to the zone database, continuing without it" + RESET);
            //e.printStackTrace();
        }
        return zoneCatalog;
    }

    // Log helpers
    private static void logCacheConnectionLost(long startTime) {
        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L, ResponseSource.CACHE));
        LoggingFeature.logDebug(DNSEventDebug.debug(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime,ResponseSource.CACHE,
                null, null, null, -1,
                "Connection to cache Server has been lost, assume internal Server error",
                null
        ));
    }

    private static void logCacheInsertFailed(long startTime) {
        LoggingFeature.logInfo(DNSEventInfo.info(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime,ResponseSource.CACHE
        ));

        LoggingFeature.logDebug(DNSEventDebug.debug(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime,ResponseSource.CACHE,
                null, null, null, -1,
                "Entries couldn't be inserted into Cache. Assume cache connection is lost",
                null
        ));
    }


    // Utilities

    // Small helper method to convert byte packets into hex
    private static String toHex(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();

        for (byte b : bytes) {
            stringBuilder.append(String.format("%02X ", b));
        }

        return stringBuilder.toString().trim();
    }

    public static boolean getTestingStatus() {
        return testing;
    }
}
