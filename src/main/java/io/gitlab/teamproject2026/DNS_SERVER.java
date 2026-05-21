package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.DNSPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class DNS_SERVER {

    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0";

    public static void main(String[] args) throws Exception {

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
        }

        // port check (delete later if 0-1024 ports are allowed)
        if (portNumber > 65535) {
            IO.println("Invalid port: " + portNumber + " — port must be between 1024 and 65535, using default " + DEFAULT_PORT);
            portNumber = DEFAULT_PORT;
        }

        String upstreamRaw = props.getProperty("dns.upstreams", "8.8.8.8, 1.1.1.1");
        int dnsTimeout = Integer.parseInt(props.getProperty("dns.timeout.ms", "2").trim());
        int maxRetries = Integer.parseInt(props.getProperty("dns.max.retries", "2").trim());
        long retryDelay = Long.parseLong(props.getProperty("dns.retry.delay.ms", "100").trim());
        String strategyString = props.getProperty("dns.strategy", "FAILOVER").trim().toUpperCase();
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

        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            IO.println("UDP server listening on " + bindIP + ":" + portNumber);
            IO.println();

            // Buffer necessary to store the packet
            byte[] buffer = new byte[StubResolver.MAX_UDP_SIZE];

            // Server loop
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                try{
                IO.println("Received packet from " + clientAddress + ":" + clientPort);

                // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                IO.println("IN: " + toHex(data));

                // Map bytedata to oop classes
                DNSPacket oopPacket = DNSPacket.fromBytes(data);
                IO.println("Assembled Packet:\n" + oopPacket);

                // Return mock response here as bytes
                byte[] response = stubResolver.resolve(data);

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        response,
                        response.length,
                        clientAddress,
                        clientPort
                );

                // Actually send the packet over our socket
                socket.send(responsePacket);

                IO.println("Send response to: " + clientAddress + ":" + clientPort);
                IO.println("OUT: " + toHex(response));
                IO.println();
            } catch(Exception e){
                IO.println("Error handling packet from " + clientAddress + ":" + clientPort + "-" + e.getMessage());
            }
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
}
