package io.gitlab.teamproject2026;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Properties;

public class DNS_SERVER {
    static void main(String[] args) throws Exception {
          
        // Layer 1: defaults
        int port_number = 4242; // Default port
        String bind_ip = "0.0.0.0";

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

        // Open UDP socket
        InetAddress bindAddress = InetAddress.getByName(bind_ip);
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
