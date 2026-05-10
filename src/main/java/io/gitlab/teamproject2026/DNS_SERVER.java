package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Properties;

public class DNS_SERVER {

    private static final int DEFAULT_PORT = 4242;
    private static final String DEFAULT_IP = "0.0.0.0";

    static void main(String[] args) throws Exception {
          
        // Layer 1: defaults
        int port_number = DEFAULT_PORT;
        String bind_ip = DEFAULT_IP;

         // Layer 2: config file
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            IO.println("No config.properties found — using defaults.");
        }

        if (props.getProperty("server.port") != null)
            port_number = Integer.parseInt(props.getProperty("server.port").trim());
        if (props.getProperty("server.ip") != null)
            bind_ip = props.getProperty("server.ip").trim();

        // Layer 3: CLI args (highest priority)
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port_number = Integer.parseInt(args[i + 1]);
                i++;
            }
            if (args[i].equals("-a") && i + 1 < args.length) {
                bind_ip = args[i + 1];
                i++;
            }
        }

        // port check (delete later if 0-1024 ports are allowed)
        if (port_number < 1024 || port_number > 65535) {
            IO.println("Invalid port: " + port_number + " — port must be between 1024 and 65535, using default " + DEFAULT_PORT);
            port_number = DEFAULT_PORT;
        }

        // Open UDP socket
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bind_ip);
        } catch (Exception e) {
            IO.println("Invalid IP address: " + bind_ip + " — using default " + DEFAULT_IP);
            bind_ip = DEFAULT_IP;
            bindAddress = InetAddress.getByName(DEFAULT_IP);
        }
        try (DatagramSocket socket = new DatagramSocket(port_number, bindAddress)) {
            IO.println("UDP server listening on " + bind_ip + " : " + port_number);

            // Buffer necessary to store the packet
            byte[] buffer = new byte[1024];

            // Server loop
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // Receive Packet
                socket.receive(packet);
                IO.println("Received packet from " + packet.getAddress() + ":" + packet.getPort());

                // Print packet bytes as hex
                byte[] data = Arrays.copyOfRange(packet.getData(),0, packet.getLength());

                // This is the important stuff. The Bytes are separated by space e.g. "F3 25 01..."
                String hexDnsPacket = toHex(data);

                IO.println(hexDnsPacket);
                IO.println();
            }
        // we can use that in case we are allowed to use ports <1024 if that is the case then 
        // delete the if statement above and uncomment this part      
        //} catch (IllegalArgumentException e) {
        //    IO.println("Invalid port: " + port_number + " — port must be between 0 and 65535");
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