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

        // port check (delete later if 0-1024 ports are allowed)
        if (port_number < 1024 || port_number > 65535) {
        IO.println("Invalid port: " + port_number + " — port must be between 1024 and 65535, using default 4242");
        port_number = 4242;
        }

        // Open UDP socket
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(bind_ip);
        } catch (Exception e) {
            IO.println("Invalid IP address: " + bind_ip + " — using default 0.0.0.0");
            bind_ip = "0.0.0.0";
            bindAddress = InetAddress.getByName("0.0.0.0");
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
