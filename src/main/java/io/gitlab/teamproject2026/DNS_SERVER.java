package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.cache.CacheHandler;
import io.gitlab.teamproject2026.cache.CacheConnectionProvider;
import io.gitlab.teamproject2026.model.ResourceRecord;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import java.sql.SQLException;


public class DNS_SERVER {

    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0"; 
    //mustn't be changed anywhere in the Server loop, as it dictates whether we are testing in the CI
    private static boolean testing=false;

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
        CacheHandler cacheHandler;
        if (true /** Later to be replaced with testing flag */){
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
                // validate Cache, if Cache cannot be validated, assume internal server error and crash
                try{
                    cacheHandler.deleteExpired();
                } catch (SQLException e) {
                    IO.println("Connection to cache Server has been lost, assume internal Server error");
                    throw e;
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

                // Return mock response here as bytes
                byte[] response = MockResponses.respond(oopPacket).toBytes();

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
}
