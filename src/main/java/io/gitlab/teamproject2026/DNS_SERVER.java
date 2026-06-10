package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.options.*;
import io.gitlab.teamproject2026.config.zones.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.logging.*;
import io.gitlab.teamproject2026.resolver.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.SQLException;


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

    // mustn't be changed anywhere in the Server loop, as it dictates whether we are
    // testing in the CI
    private static boolean testing = false;

    private static String forwardMode;
    private static ResolverSection resolverOptions;
    // @TODO database should use these
    private static List<String> zoneCatalog;
    private static StubResolver stubResolver;
    private static CacheHandler cacheHandler;
    private static ZoneHandler zoneHandler;
    private static int portNumber;
    private static String bindIP;
    private static InetAddress bindAddress;
    private static long startTime;

    public record DNSPayload(
            LinkedList<ResourceRecord> answer,
            LinkedList<ResourceRecord> authority,
            LinkedList<ResourceRecord> additional,
            LinkedList<QuestionSection> notCached) {
    }

    static void main(String[] args) {
        initializeServer(args);
        new Thread(DNS_SERVER::handleUDP).start();
        new Thread(DNS_SERVER::handleTCP).start();
    }

    private static void handleUDP(){
        // Open UDP socket
        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println(GREEN + "[SERVER] UDP server listening on " + bindIP + ":" + portNumber + RESET);

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (true) {
                // validate Cache, if Cache cannot be validated, assume internal server error
                // and crash. Disable during testing
                validateDatabase();


                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                IO.println(BLUE + "[SERVER] Received UDP packet from " + clientAddress + ":" + clientPort + RESET);
                // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                IO.println("[SERVER] IN: " + toHex(data));

                // Handle the query and receive a byte array as response
                byte[] byteResponse = handleQuery(data);

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        byteResponse,
                        byteResponse.length,
                        clientAddress,
                        clientPort);

                // Actually send the packet over our socket
                socket.send(responsePacket);

                IO.println(BLUE + "[SERVER] Send UDP response to: " + clientAddress + ":" + clientPort + RESET);
                IO.println("[SERVER] OUT: " + toHex(byteResponse));
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private static void handleTCP(){
        // Open TCP socket
        try (ServerSocket serverSocket = new ServerSocket(portNumber, 50, bindAddress);) {
            IO.println(GREEN + "[SERVER] TCP server listening on " + bindIP + ":" + portNumber + RESET);

            // Server loop
            while (true) {
                // validate Cache, if Cache cannot be validated, assume internal server error
                // and crash. Disable during testing
                validateDatabase();

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
                    IO.println(BLUE + "[SERVER] Received TCP packet from " + clientAddress + ":" + clientPort + RESET);

                    IO.println("[SERVER] IN: " + toHex(data));

                    byte[] response = handleQuery(data);

                    if (response.length > 65535) {
                        System.err.println(RED + "[SERVER-ERROR] TCP DNS response too large" + RESET);
                        continue;
                    }

                    output.writeShort(response.length);
                    output.write(response);
                    output.flush();

                    IO.println(BLUE + "[SERVER] Sent TCP response to " + clientAddress + ":" + clientPort + RESET);
                    IO.println("[SERVER] OUT: " + toHex(response));


                } catch (IOException e) {
                    System.err.println(RED + "[SERVER-ERROR] Failed to handle TCP client" + RESET);
                }
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }




    // Startup helpers
    private static void initializeServer(String[] args){
        IO.println("\n--------------------------------[ DNSSERVER ]--------------------------------- ");

        // named.conf
        Config serverConfig = ConfigManager.loadConfig("named.conf");

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
            System.err.println(RED + "Invalid port: " + portNumber + " — port must be between 0 and 65535, using default "
                    + DEFAULT_PORT + RESET);
            portNumber = DEFAULT_PORT;
        }

        forwardMode = options.forwardMode().toLowerCase();// Determines if we should only forward or also recurse ourselves

        List<Forwarder> forwarders = options.forwarders().stream()
                .map(ip -> new Forwarder(ip, 53, ip))
                .toList();
        stubResolver = new StubResolver(
                forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());

        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            System.err.println(RED + "Invalid IP address: " + bindIP + " — using default " + DEFAULT_IP + RESET);
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
    private static void validateDatabase(){
        startTime = System.currentTimeMillis();
        if (testing || cacheHandler == null) {
            return;
        }
        try {
            cacheHandler.deleteExpired();
        } catch (SQLException e) {
            IO.println(RED + "[SERVER-ERROR] Connection to database Server has been lost, assume internal Server error" + RESET);
            // log and enable testing mode as cache entries cannot be ensured to be valid
            logCacheConnectionLost(startTime);
            testing = true;
        }
    }

    // Query handling logic
    private static byte[] handleQuery(byte[] data){
        // Map byteData to oop classes
        DNSPacket oopPacket = DNSPacket.fromBytes(data);

        // Uncomment this if you need to debug outgoing packets
        //IO.println("[SERVER] Assembled Packet:\n" + oopPacket);

        // Respond: If requested RR is cached, return RR else return stub Response

        DNSPayload payload = new DNSPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                new LinkedList<>());

        // Todo A2 currently only test
        System.out.println(zoneCatalog.stream().filter((e) -> oopPacket.getQuestionSections()[0].getNameString().endsWith("." + e)).toList().getFirst());
        try {
            System.out.println(Arrays.toString(zoneHandler.lookup(oopPacket.getQuestionSections()[0].getNameString(), oopPacket.getQuestionSections()[0].getQtype(), "example.com")));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //needed for cache logging:
        byte[] idBytes = oopPacket.getId();
        String id = "0x" + HexFormat.of().formatHex(idBytes);
        if (!testing && cacheHandler != null) {
            payload = cacheLookUp(oopPacket, id);
        } else {
            // if testing is active, do not check cache
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                payload.notCached.add(request);
                LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L)); //since cache isn't checked latency is 0
                LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L,
                        resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "testing is active, do not check cache", null));

            }
        }

        // StubResolver should only be queried if there are queries not answered by the
        // cache
        if (!payload.notCached.isEmpty()) {
            // if (testing){
            payload = queryStubResolver(payload, data);
        }

        // Build response packet
        DNSPacket response = buildResponsePacket(payload, oopPacket);

        // convert response packet to Bytes
        byte[] byteResponse = response.toBytes();

        // if DNS-Packet exceeds maximum UDP Payload size, truncate it and set the tc
        // flag
        if (byteResponse.length >= UDP_MAX_PAYLOAD_SIZE) {
            byte[] byteResponseTruncated = new byte[UDP_MAX_PAYLOAD_SIZE];
            response.setTc(true);
            byte[] truncatedSource = response.toBytes();
            System.arraycopy(truncatedSource, 0, byteResponseTruncated, 0, UDP_MAX_PAYLOAD_SIZE);
            byteResponse = byteResponseTruncated;
        }

        return byteResponse;
    }

    private static DNSPayload cacheLookUp(DNSPacket oopPacket, String id){
        DNSPayload payload = new DNSPayload(
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>(),
                new LinkedList<>()
        );
        // query cache and safe results in payload
        long cacheStart = System.currentTimeMillis();
        try {
            payload = queryCache(oopPacket, cacheHandler);
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                String domain = request.nameToString();
                RRType qType = request.getQtype();
                if (payload.notCached.contains(request)) {
                    IO.println(BLUE + "[CACHE] Miss! Couldn't find: " + qType + " of \"" + domain + "\"" + RESET );
                    LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, domain, qType, System.currentTimeMillis() - cacheStart));
                    LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, domain, qType, 0L, resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "Not found in database", null));

                } else {
                    IO.println(GREEN + "[CACHE] Hit! Found: " + qType + " Record of " + domain + RESET );
                    LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_HIT, id, domain, qType, System.currentTimeMillis() - cacheStart));
                    LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_HIT, id, domain, qType, 0L,
                            resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, null, null));

                }

            }

        } catch (SQLException e) {
            // log
            LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L)); //no query, so everything except DNSEventType null

            LoggingFeature.logDebug(DNSEventDebug.debug(
                    DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - cacheStart,
                    null, null, null, -1,
                    "Connection to cache Server has been lost, assume internal Server error",
                    null));//debug fields.

            // connection to cache couldn't be reached so all queries have to be resolved
            // through the resolver
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                payload.notCached.add(request);
                LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), System.currentTimeMillis() - cacheStart));
                LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), System.currentTimeMillis() - cacheStart,
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
                payload.notCached.toArray(new QuestionSection[0]));
        uncachedOOPacket.setQdcount(payload.notCached.size());

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
     * notCached part of the record
     *
     * @param query        the DNS query which is to be resolved
     * @param cacheHandler the CacheHandler with which the function accesses teh
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
                                authority.add(record);
                                String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                Collections.addAll(additional, cacheHandler.lookup(nsHost, RRType.A));
                                Collections.addAll(additional, cacheHandler.lookup(nsHost, RRType.AAAA));
                                break;
                            }
                            case MX: {
                                answer.add(record);
                                String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.A));
                                Collections.addAll(additional, cacheHandler.lookup(mxHost, RRType.AAAA));
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
    //                 to "notCached" so the stub only resolves the remaining tail; return true.
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
    private static DNSPacket buildResponsePacket(DNSPayload payload, DNSPacket query) {
        DNSPacket response = new DNSPacket();
        response.setId(query.getId());
        response.setQr(true);
        response.setOpcode(query.getOpcode());
        response.setAa(false);
        response.setTc(false);
        response.setRa(true);
        response.setZ((byte) 0);
        response.setQdcount(query.getQdcount());
        response.setAncount(payload.answer.size());
        response.setNscount(payload.authority.size());
        response.setArcount(payload.additional.size());
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
            for (ZoneSection zone : declaredZones){
                // Collect all zones specified in the config
                DNSZone loadedZone = ConfigManager.loadZoneFile(dir, zone.file(), zone.name());
                if (loadedZone != null) {
                    zoneHandler.insertAll(loadedZone.getRecords().toArray(new ResourceRecord[0]), zone.name());
                    zoneCatalog.add(zone.name());
                }
            }
        }catch (SQLException e) {
            System.err.println(RED + "Failed to insert zone files to the zone database, continuing without it" + RESET);
        }

        return zoneCatalog;
    }

    // Log helpers
    private static void logCacheConnectionLost(long startTime) {
        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L));
        LoggingFeature.logDebug(DNSEventDebug.debug(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime,
                null, null, null, -1,
                "Connection to cache Server has been lost, assume internal Server error",
                null
        ));
    }

    private static void logCacheInsertFailed(long startTime) {
        LoggingFeature.logInfo(DNSEventInfo.info(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime
        ));

        LoggingFeature.logDebug(DNSEventDebug.debug(
                DNSEventType.CACHE_ERROR,
                null, null, null,
                System.currentTimeMillis() - startTime,
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
