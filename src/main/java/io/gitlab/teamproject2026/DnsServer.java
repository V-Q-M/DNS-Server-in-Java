package io.gitlab.teamproject2026;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class DnsServer {
    static void main(String[] args) throws Exception {
        int port_number = 4242; // Default port

        // Parse command line arg
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port_number = Integer.parseInt(args[i + 1]);
            }
        }

        DatagramSocket socket = new DatagramSocket(port_number);

        IO.println("UDP server listening on port " + port_number);

        byte[] buffer = new byte[1024];


        // Server loop

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // Receive Packet
            socket.receive(packet);
            IO.println("Received packet from " + packet.getAddress() + ":" + packet.getPort());

            // Print packet bytes as hex
            byte[] data = packet.getData();

            for (int i = 0; i < packet.getLength(); i++) {
                System.out.printf("%02X ", data[i]);
            }

            System.out.println("\n");
        }
    }
}
