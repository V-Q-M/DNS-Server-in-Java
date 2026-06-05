package io.gitlab.teamproject2026;

import java.io.IOException;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.model.DNSPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

import java.sql.SQLException;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.logging.DNSEventInfo;
import io.gitlab.teamproject2026.logging.DNSEventDebug;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.LoggingFeature;
import io.gitlab.teamproject2026.resolver.Forwarder;
import io.gitlab.teamproject2026.resolver.StubResolver;


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

    public record dnsPayload(
            LinkedList<ResourceRecord> answer,
            LinkedList<ResourceRecord> authority,
            LinkedList<ResourceRecord> additional,
            LinkedList<QuestionSection> notCached) {
    }

    private static List<DNSZone> loadedZones;

    static void main(String[] args) throws Exception {
        IO.println("\n--------------------------------[ DNSSERVER ]--------------------------------- ");
        // named.conf
        Config namedConfig = ConfigManager.loadConfig("named.conf");
        OptionSection options = namedConfig.options();
        ResolverSection resolverOptions = options.resolverSection();

        try {
            loadedZones = loadZonesFromConf(namedConfig);
        } catch (IOException e){
            System.err.println("ERROR: Failed parsing zone files");
            loadedZones = null;
        }

        // Default options from the config
        int portNumber = options.port();
        String bindIP = options.ipAddress();

        // Overwrite with CLI args (highest priority)
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                portNumber = Integer.parseInt(args[i + 1]);
                i++;
            }
            if (args[i].equals("-a") && i + 1 < args.length) {
                bindIP = args[i + 1];
                i++;
            }
            if (args[i].equals("--testing")) {
                testing = true;
            }
        }

        // port check
        if (portNumber < 0 || portNumber > 65535) {
            System.err.println(RED + "Invalid port: " + portNumber + " — port must be between 0 and 65535, using default "
                    + DEFAULT_PORT + RESET);
            portNumber = DEFAULT_PORT;
        }

        String forwardMode = options.forwardMode(); // Determines if we should only forward or also recurse ourselves

        List<Forwarder> forwarders = options.forwarders().stream()
                .map(ip -> new Forwarder(ip, 53, ip))
                .toList();
        StubResolver stubResolver = new StubResolver(
                forwarders, resolverOptions.timeout(), resolverOptions.maxRetries(), resolverOptions.retryDelay(), resolverOptions.strategy());

        // Open UDP socket
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            System.err.println(RED + "Invalid IP address: " + bindIP + " — using default " + DEFAULT_IP + RESET);
            bindIP = DEFAULT_IP;
            bindAddress = InetAddress.getByName(DEFAULT_IP);
        }

        // esthablish Cache Connection
        CacheHandler cacheHandler = null;
        long startTime = System.currentTimeMillis();
        if (!testing) {
            try {
                cacheHandler = new CacheHandler(new CacheConnectionProvider());
            } catch (ClassNotFoundException e) {
                System.err.println("JDBC jar couldn't be located, perhaps the classpath isn't set up correctly?");
                throw e;
            } catch (SQLException e) {
                System.err
                        .println("Connection to the Database couldn't be esthablished, is the Docker-Server running?");
                // log and enable testing mode to ignore Cache, as initial connection couldn't
                LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L)); //no query, so everything except DNSEventType null
                LoggingFeature.logDebug(DNSEventDebug.debug(
                            DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,
                            null, null, null, -1,
                            "Connection to cache Server has been lost, assume internal Server error",
                            null));//debug fields.

                    // be set up
                    testing = true;

            }
        }

        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println(GREEN + "[SERVER] UDP server listening on " + bindIP + ":" + portNumber + RESET);

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (true) {
                // validate Cache, if Cache cannot be validated, assume internal server error
                // and crash. Disable during testing
                startTime = System.currentTimeMillis();
                if (!testing) {
                    try {
                        cacheHandler.deleteExpired();
                    } catch (SQLException e) {
                        IO.println(RED + "[SERVER] ERROR: Connection to cache Server has been lost, assume internal Server error" + RESET);
                        // log and enable testing mode as cache entries cannot be ensured to be valid
                        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, 0L)); //no query, so everything except DNSEventType null
                        LoggingFeature.logDebug(DNSEventDebug.debug(
                                    DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,
                                    null, null, null, -1,
                                    "Connection to cache Server has been lost, assume internal Server error",
                                    null));//debug fields.

                            testing = true;

                    }
                }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                IO.println(BLUE + "[SERVER] Received packet from " + clientAddress + ":" + clientPort + RESET);

                // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                IO.println("[SERVER] IN: " + toHex(data));

                // Map bytedata to oop classes
                DNSPacket oopPacket = DNSPacket.fromBytes(data);

                // Uncomment this if you need to debug outgoing packets
                //IO.println("[SERVER] Assembled Packet:\n" + oopPacket);

                // Respond: If requested RR is cached, return RR else return stub Response

                dnsPayload payload = new dnsPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                        new LinkedList<>());
                //needed for cache logging:
                byte[] idBytes = oopPacket.getId();
                String id = "0x" + java.util.HexFormat.of().formatHex(idBytes);

                if (!testing) {
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
                } else {
                    // if testing is active, do not check cache
                    for (QuestionSection request : oopPacket.getQuestionSections()) {
                        payload.notCached.add(request);
                        LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L)); //since cache isn't checked latency is 0
                        LoggingFeature.logDebug(DNSEventDebug.debug(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L,
                                resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "testing is active, do not check cache", null));

                    }
                }

                // Stubresolver should only be queried if there are queries not answered by the
                // cache
                if (payload.notCached.size() > 0) {
                    // if (testing){

                    // Build packet to be send to Stub resolver
                    // The ID should not have to be changed, as the Server should not recieve any
                    // other packages using this ID from the original sender
                    DNSPacket uncachedOOPacket = DNSPacket.fromBytes(data);
                    uncachedOOPacket.setQuestionSections(
                            payload.notCached.toArray(new QuestionSection[payload.notCached.size()]));
                    uncachedOOPacket.setQdcount(payload.notCached.size());

                    // Get Stub response
                    byte[] rawResponse = stubResolver.resolve(uncachedOOPacket.toBytes());
                    DNSPacket stubResponse = DNSPacket.fromBytes(rawResponse);

                    // Extract RRs from Stub Response and cache them if testing mode is not enabled
                    // caching
                    if (!testing) {
                        startTime = System.currentTimeMillis(); //set new startTime for logging of CACHE_ERROR
                        try {
                            cacheHandler.insertAll(stubResponse.getAnswerSections());
                            cacheHandler.insertAll(stubResponse.getAuthoritySections());
                            cacheHandler.insertAll(stubResponse.getAdditionalSections());
                        } catch (SQLException e) {
                            System.err.println("[SERVER] ERROR: Entries couldn't be inserted into Cache. Assume cache connection is lost");
                            // log
                            LoggingFeature.logInfo(DNSEventInfo.info(DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime)); //no query, so everything except DNSEventType null
                            LoggingFeature.logDebug(DNSEventDebug.debug(
                                        DNSEventType.CACHE_ERROR, null, null, null, System.currentTimeMillis() - startTime,
                                        null, null, null, -1,
                                        "Entries couldn't be inserted into Cache. Assume cache connection is lost",
                                        null));//debug fields.


                        }
                    }

                    // extracting and adding to answers
                    for (ResourceRecord answers : stubResponse.getAnswerSections()) {
                        payload.answer.add(answers);
                    }
                    for (ResourceRecord authorities : stubResponse.getAuthoritySections()) {
                        payload.authority.add(authorities);
                    }
                    for (ResourceRecord additionals : stubResponse.getAdditionalSections()) {
                        payload.additional.add(additionals);
                    }
                }

                // Build response packet
                DNSPacket response = buildResponsePacket(payload, oopPacket);

                // convert response packet to Bytes
                byte[] byteResponse = response.toBytes();

                // if DNS-Packet exceeds maximum UDP Payload size, truncate it and set the tc
                // flag
                if (byteResponse.length >= UDP_MAX_PAYLOAD_SIZE) {
                    byte[] byteResponsetruncated = new byte[UDP_MAX_PAYLOAD_SIZE];
                    response.setTc(true);
                    byte[] truncatedSource = response.toBytes();
                    System.arraycopy(truncatedSource, 0, byteResponsetruncated, 0, UDP_MAX_PAYLOAD_SIZE);
                    byteResponse = byteResponsetruncated;
                }

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        byteResponse,
                        byteResponse.length,
                        clientAddress,
                        clientPort);

                // Actually send the packet over our socket
                socket.send(responsePacket);

                IO.println(BLUE + "[SERVER] Send response to: " + clientAddress + ":" + clientPort + RESET);
                IO.println("[SERVER] OUT: " + toHex(byteResponse));
            }
        }
    }

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
     * @return A dnsPayload record containing the resolved Reccords and the Records
     *         that have yet to be resolved
     * @throws SQLException if the cache cannot be accessed
     */
    public static dnsPayload queryCache(DNSPacket query, CacheHandler cacheHandler) throws SQLException {

        LinkedList<ResourceRecord> answer = new LinkedList<>();
        LinkedList<ResourceRecord> authority = new LinkedList<>();
        LinkedList<ResourceRecord> additional = new LinkedList<>();
        LinkedList<QuestionSection> notCached = new LinkedList<>();

        try {

            // for each query, check if the requested RR is cached, if not add to the
            // Queries sent to the Stubresolver
            for (QuestionSection request : query.getQuestionSections()) {
                String qname = String.join(".", request.getName());
                ResourceRecord[] cachedRecords = cacheHandler.lookup(qname, request.getQtype());

                if (cachedRecords.length != 0) {
                    for (ResourceRecord record : cachedRecords) {
                        switch (record.getType()) {
                            case NS: {
                                authority.add(record);
                                String nsHost = String.join(".", ((RDataNS) record.getRdata()).getNsdname());
                                for (ResourceRecord adress : cacheHandler.lookup(nsHost, RRType.A))
                                    additional.add(adress);
                                for (ResourceRecord adress : cacheHandler.lookup(nsHost, RRType.AAAA))
                                    additional.add(adress);
                                break;
                            }
                            case MX: {
                                answer.add(record);
                                String mxHost = String.join(".", ((RDataMX) record.getRdata()).getExchange());
                                for (ResourceRecord adress : cacheHandler.lookup(mxHost, RRType.A))
                                    additional.add(adress);
                                for (ResourceRecord adress : cacheHandler.lookup(mxHost, RRType.AAAA))
                                    additional.add(adress);
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
                    "Connection to Server couldn't be esthablished. Assume internal Server error. Is the Postgres-Docker running?");
            throw e;
        }

        return new dnsPayload(answer, authority, additional, notCached);
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
                for (ResourceRecord record : targetRecords) answer.add(record);
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
    private static DNSPacket buildResponsePacket(dnsPayload payload, DNSPacket query) {
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
        response.setAnswerSections(payload.answer.toArray(new ResourceRecord[payload.answer.size()]));
        response.setAuthoritySections(payload.authority.toArray(new ResourceRecord[payload.authority.size()]));
        response.setAdditionalSections(payload.additional.toArray(new ResourceRecord[payload.additional.size()]));
        return response;
    }

    private static List<DNSZone> loadZonesFromConf(Config config) throws IOException {
        OptionSection options = config.options();
        String dir = options.dir();
        List<ZoneSection> declaredZones = config.zones();

        List<DNSZone> zones = new ArrayList<>(); //@TODO Aufgabe05
        for (ZoneSection zone : declaredZones){
            // Collect all zones specified in the config
            zones.add(ConfigManager.loadZoneFile(dir, zone.file(), zone.name())); //@TODO Zonecatalog
        }
        return zones;
    }


}
