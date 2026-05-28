package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.model.ResourceRecord;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.sql.SQLException;


public class DNS_SERVER {

    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0"; 
    private static final int UDP_MAX_PAYLOAD_SIZE = 65507; // see https://en.wikipedia.org/wiki/User_Datagram_Protocol#UDP_datagram_structure
    //mustn't be changed anywhere in the Server loop, as it dictates whether we are testing in the CI
    private static boolean testing=false;

    private record dnsPayload(
        LinkedList<ResourceRecord> answer,
        LinkedList<ResourceRecord> authority,
        LinkedList<ResourceRecord> additional,
        LinkedList<QuestionSection> notCached
    ){};

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
        if (portNumber > 65535) {
            IO.println("Invalid port: " + portNumber + " — port must be between 1024 and 65535, using default " + DEFAULT_PORT);
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
        if (!testing){
            try{
                cacheHandler = new CacheHandler( new CacheConnectionProvider());
            } catch (ClassNotFoundException e) {
                System.err.println("JDBC jar couldn't be located, perhaps the classpath isn't set up correctly?");
                throw e;
            } catch (SQLException e) {
                System.err.println("Connection to the Database couldn't be esthablished, is the Docker-Server running?");
                throw e;
            }
        }

        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println("UDP server listening on " + bindIP + ":" + portNumber);
            IO.println();

            // Buffer necessary to store the packet
            byte[] buffer = new byte[1024];

            // Server loop
            while (true) {
                // validate Cache, if Cache cannot be validated, assume internal server error and crash. Disable during testing
                if (!testing){
                    try{
                        cacheHandler.deleteExpired();
                    } catch (SQLException e) {
                        IO.println("Connection to cache Server has been lost, assume internal Server error");
                        throw e;
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

                // Respond: If requested RR is cached, return RR else return mock Response

                dnsPayload payload = new dnsPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(), new LinkedList<>());

                if (!testing){
                    // query cache and safe results in payload
                    payload = queryCache(oopPacket, cacheHandler);
                } else {
                    // if testing is active, do not check cache 
                    for (QuestionSection request:oopPacket.getQuestionSections()) {
                        payload.notCached.add(request);
                    };
                }

                
                // MockResponder/ Stubresolver should only be queried if there are queries not answered by the cache
                if (payload.notCached.size() > 0){

                    // Build packet to be send to either Mock resolver or Stub resolver
                    // The ID should not have to be changed, as the Server should not recieve any other packages using this ID from the original sender
                    DNSPacket uncachedOOPacket = DNSPacket.fromBytes(data);
                    uncachedOOPacket.setQuestionSections(payload.notCached.toArray(new QuestionSection[payload.notCached.size()]));
                    uncachedOOPacket.setQdcount(payload.notCached.size());


                    // Get Mock response, later needs to be stub response
                    DNSPacket mockResponse = MockResponses.respond(uncachedOOPacket);

                    // Extract RRs from Mock Response and cache them if testing mode is not enabled
                    // caching
                    if (!testing){
                        cacheHandler.insertAll(mockResponse.getAnswerSections());
                        cacheHandler.insertAll(mockResponse.getAuthoritySections());
                        cacheHandler.insertAll(mockResponse.getAdditionalSections());
                    }

                    // extracting and adding to answers
                    for (ResourceRecord answers:mockResponse.getAnswerSections()){
                        payload.answer.add(answers);
                    }
                    for (ResourceRecord authorities:mockResponse.getAuthoritySections()){
                        payload.authority.add(authorities);
                    }
                    for (ResourceRecord additionals:mockResponse.getAdditionalSections()){
                        payload.additional.add(additionals);
                    }
                }

                //Build response packet
                DNSPacket response = buildResponsePacket(payload, oopPacket);

                // convert response packet to Bytes
                byte[] byteResponse = response.toBytes();

                // if DNS-Packet exceeds maximum UDP Payload size, truncate it and set the tc flag
                if (byteResponse.length >= UDP_MAX_PAYLOAD_SIZE){
                    byte[] byteResponsetruncated = new byte[UDP_MAX_PAYLOAD_SIZE];
                    response.setTc(true);
                    System.arraycopy(response.toBytes(), 0, byteResponsetruncated, 0, UDP_MAX_PAYLOAD_SIZE);
                    byteResponse = byteResponsetruncated;
                }

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        byteResponse,
                        byteResponse.length,
                        clientAddress,
                        clientPort
                );

                // Actually send the packet over our socket
                socket.send(responsePacket);

                IO.println("Send response to: " + clientAddress + ":" + clientPort);
                IO.println("OUT: " + toHex(byteResponse));
                IO.println();
            }
        }
    }

    // Small helper method to convert byte packets into hex
    private static String toHex(byte[] bytes){
        StringBuilder stringBuilder = new StringBuilder();

        for (byte b : bytes) {
            stringBuilder.append(String.format("%02X ", b));
        }

        return stringBuilder.toString().trim();
    }

    public static boolean getTestingStatus(){
        return testing;
    }

    /** Queries the Cache using a given cacheHandler and a dns query. If the Cache contains the requested records, it returns them in the corresponding section of the Record.
     * Also Returns A and AAAA records for NS and MX records
     * Packets that couldn't be resolved with the cache itself are stored in the notCached part of the record
     *
     * @param query the DNS query which is to be resolved
     * @param cacheHandler the CacheHandler with which the function accesses teh cache
     * @return A dnsPayload record containing the resolved Reccords and the Records that have yet to be resolved
     * @throws SQLException if the cache cannot be accessed
     */
    private static dnsPayload queryCache(DNSPacket query, CacheHandler cacheHandler) throws SQLException{

        LinkedList<ResourceRecord> answer = new LinkedList<>();
        LinkedList<ResourceRecord> authority = new LinkedList<>();
        LinkedList<ResourceRecord> additional= new LinkedList<>();
        LinkedList<QuestionSection> notCached = new LinkedList<>();

        try{


            // for each query, check if the requested RR is cached, if not add to the Queries sent to the Stubresolver/Mockresolver
            for (QuestionSection request:query.getQuestionSections()) {
                ResourceRecord[] cachedRecords = cacheHandler.lookup(String.join(".",request.getName()), request.getQtype());

                // if RRs are cached, add them to the corresponding sections, else add them to a non resolved list to be resolved later
                if (cachedRecords.length != 0) {
                    for (ResourceRecord record: cachedRecords){
                        switch (record.getType()){
                            case NS : {authority.add(record);

                                // check if ipv4 Adress or Ipv6 for record is also known and add it to the additional section
                                for (ResourceRecord adress:cacheHandler.lookup(String.join(".",record.getName()), RRType.A)){
                                    additional.add(adress);
                                }
                                for (ResourceRecord adress:cacheHandler.lookup(String.join(".",record.getName()), RRType.AAAA)){
                                    additional.add(adress);
                                }
                            };
                            case MX : {answer.add(record);
                                // check if ipv4 Adress or Ipv6 for record is also known and add it to the additional section
                                for (ResourceRecord adress:cacheHandler.lookup(String.join(".",record.getName()), RRType.A)){
                                    additional.add(adress);
                                }
                                for (ResourceRecord adress:cacheHandler.lookup(String.join(".",record.getName()), RRType.AAAA)){
                                    additional.add(adress);
                                }
                            };
                            case A,AAAA,TXT,SOA,PTR,CNAME : answer.add(record);
                        }
                    }
                } else {
                    notCached.add(request);
                }


            }
        } catch (SQLException e) {
            System.err.println("Connection to Server couldn't be esthablished. Assume internal Server error. Is the Postgres-Docker running?");
            throw e;
        }

        return new dnsPayload(answer, authority, additional, notCached);
    }

    /** Build a response packet given a Payload for a given query
     *
     * @param payload The Payload that is to be put into the response packet
     * @param query The Query that is to be answered
     * @return a DNS-Packet in oop format
     */
    private static DNSPacket buildResponsePacket(dnsPayload payload, DNSPacket query){
        DNSPacket response = new DNSPacket();
        response.setId(query.getId());
        response.setQr(true);
        response.setOpcode(query.getOpcode());
        response.setAa(false); // cannot be authoratative as our server doesn't have a zone just yet
        response.setTc(false); // checked later on again to make sure the package doesn't exceed max udp payload size
        response.setRa(true); // at this point our server handels every request recursively
        response.setZ((byte)0);
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
