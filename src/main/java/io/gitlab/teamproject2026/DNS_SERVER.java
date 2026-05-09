package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.DNSPacket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

public class DNS_SERVER {
    static void main(String[] args) throws Exception {
        int port_number = 4242; // Default port

        // Parse command line arg
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port_number = Integer.parseInt(args[i + 1]);
            }
        }

        // Open UDP socket
        DatagramSocket socket = new DatagramSocket(port_number);
        IO.println("UDP server listening on port " + port_number);

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

            DNSPacket packet1 = DNSPacket.from_bytes(data);
            System.out.println("" + packet1);
            // This is the important stuff. The Bytes are separated by space e.g. "F3 25 01..."
            String hexDnsPacket = toHex(data);

            IO.println(hexDnsPacket);
            IO.println();
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
