package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;

import io.gitlab.teamproject2026.model.DNSPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import java.util.LinkedList;
import java.sql.SQLException;
import io.gitlab.teamproject2026.model.rdata.RDataCNAME;
import io.gitlab.teamproject2026.model.rdata.RDataMX;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.model.ResourceRecord;

public class DNS_SERVER {

    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final int UDP_MAX_PAYLOAD_SIZE = 65507; // see https://en.wikipedia.org/wiki/User_Datagram_Protocol
    // mustn't be changed anywhere in the Server loop, as it dictates whether we are
    // testing in the CI
    private static boolean testing = false;

    public record dnsPayload(
            LinkedList<ResourceRecord> answer,
            LinkedList<ResourceRecord> authority,
            LinkedList<ResourceRecord> additional,
            LinkedList<QuestionSection> notCached) {
    };

    static void main(String[] args) throws Exception {

        // Layer 1: defaults
        int portNumber = DEFAULT_PORT;
        String bindIP = DEFAULT_IP;

        // Layer 2: config file
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            IO.println("No config.properties found — using defaults.");
        }

        if (props.getProperty("server.port") != null)
            portNumber = Integer.parseInt(props.getProperty("server.port").trim());
        if (props.getProperty("server.ip") != null)
            bindIP = props.getProperty("server.ip").trim();

        // Layer 3: CLI args (highest priority)
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

        // port check (delete later if 0-1024 ports are allowed)
        if (portNumber < 0 || portNumber > 65535) {
            IO.println("Invalid port: " + portNumber + " — port must be between 1024 and 65535, using default "
                    + DEFAULT_PORT);
            portNumber = DEFAULT_PORT;
        }

        String upstreamRaw = props.getProperty("dns.upstreams", "8.8.8.8, 1.1.1.1");
        int dnsTimeout = Integer.parseInt(props.getProperty("dns.timeout.ms", "2000").trim());
        int maxRetries = Integer.parseInt(props.getProperty("dns.max.retries", "2").trim());
        long retryDelay = Long.parseLong(props.getProperty("dns.retry.delay.ms", "100").trim());
        String strategyString = props.getProperty("dns.strategy", "FAILOVER").replaceAll("\\r", "").trim().toUpperCase();

        StubResolver.Strategy strategy = StubResolver.Strategy.valueOf(strategyString);
        List<StubResolver.NameServer> nameServers = Arrays.stream(upstreamRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ip -> new StubResolver.NameServer(ip, 53, ip))
                .toList();
        StubResolver stubResolver = new StubResolver(nameServers, dnsTimeout, maxRetries, retryDelay, strategy);

        // Open UDP socket
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bindIP);
        } catch (Exception e) {
            IO.println("Invalid IP address: " + bindIP + " — using default " + DEFAULT_IP);
            bindIP = DEFAULT_IP;
            bindAddress = InetAddress.getByName(DEFAULT_IP);
        }

        // esthablish Cache Connection
        CacheHandler cacheHandler = null;
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
                // be set up
                testing = true;
            }
        }

        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println("UDP server listening on " + bindIP + ":" + portNumber);
            IO.println();

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (true) {
                // validate Cache, if Cache cannot be validated, assume internal server error
                // and crash. Disable during testing
                if (!testing) {
                    try {
                        cacheHandler.deleteExpired();
                    } catch (SQLException e) {
                        IO.println("Connection to cache Server has been lost, assume internal Server error");
                        // log and enable testing mode as cache entries cannot be ensured to be valid
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
                IO.println("Received packet from " + clientAddress + ":" + clientPort);

                // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                IO.println("IN: " + toHex(data));

                // Map bytedata to oop classes
                DNSPacket oopPacket = DNSPacket.fromBytes(data);
                IO.println("Assembled Packet:\n" + oopPacket);

                // Respond: If requested RR is cached, return RR else return stub Response

                dnsPayload payload = new dnsPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                        new LinkedList<>());

                if (!testing) {
                    // query cache and safe results in payload
                    try {
                        payload = queryCache(oopPacket, cacheHandler);
                    } catch (SQLException e) {
                        // log
                        // connection to cache couldn't be reached so all queries have to be resolved
                        // through the resolver
                        for (QuestionSection request : oopPacket.getQuestionSections()) {
                            payload.notCached.add(request);
                        }
                        ;
                    }
                } else {
                    // if testing is active, do not check cache
                    for (QuestionSection request : oopPacket.getQuestionSections()) {
                        payload.notCached.add(request);
                    }
                    ;
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
                        try {
                            cacheHandler.insertAll(stubResponse.getAnswerSections());
                            cacheHandler.insertAll(stubResponse.getAuthoritySections());
                            cacheHandler.insertAll(stubResponse.getAdditionalSections());
                        } catch (SQLException e) {
                            IO.println("Entries couldn't be inserted into Cache. Assume cache connection is lost");
                            // log
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

                IO.println("Send response to: " + clientAddress + ":" + clientPort);
                IO.println("OUT: " + toHex(byteResponse));
                IO.println();
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
                    // skip CNAME queries
                    if (!resolveCnameFromCache(qname, request.getQtype(), answer, cacheHandler)) {
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

    // Returns false if the CNAME or its target is not cached, so the caller can
    // forward to the stub.
    private static boolean resolveCnameFromCache(
            String name, RRType targetType,
            LinkedList<ResourceRecord> answer,
            CacheHandler cacheHandler) throws SQLException {
        ResourceRecord[] cnames = cacheHandler.lookup(name, RRType.CNAME);
        if (cnames.length == 0)
            return false;
        String target = String.join(".", ((RDataCNAME) cnames[0].getRdata()).getCname());
        ResourceRecord[] targetRecords = cacheHandler.lookup(target, targetType);
        if (targetRecords.length == 0)
            return false;
        answer.add(cnames[0]);
        for (ResourceRecord record : targetRecords)
            answer.add(record);
        return true;
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

}
