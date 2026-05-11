package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.data.MockResponses;
import io.gitlab.teamproject2026.model.DNSPacket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.Array;
import java.util.Arrays;

public class DNS_SERVER {
    static void main(String[] args) throws Exception {
        int port_number = 4242; // Default port
        String bind_ip = "0.0.0.0"; // Default IP

        // Parse command line arg
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port_number = Integer.parseInt(args[i + 1]);
            }
            if (args[i].equals("-a") && i + 1 < args.length){
                bind_ip = args[i+1];
            }
        }

        InetAddress bindAddress = InetAddress.getByName(bind_ip);

        // Open UDP socket
        DatagramSocket socket = new DatagramSocket(port_number, bindAddress);
        System.out.println("UDP server listening on " + bind_ip + ":" + port_number);

        // Buffer necessary to store the packet
        byte[] buffer = new byte[1024];

        // Server loop
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // Receive Packet
            socket.receive(packet);
            // Print packet bytes as hex
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            InetAddress clientAddress = packet.getAddress();
            int clientPort = packet.getPort();

            System.out.println("Received packet from " + clientAddress + ":" + clientPort);

            // This is the important stuff. The Bytes are separated by space e.g. "F3 25 01..."
            String hexDnsPacket = toHex(data);

            System.out.println(hexDnsPacket);
            System.out.println();

            // Parse the byte array to a DNSPacket
            DNSPacket requestPacket = DNSPacket.from_bytes(data);

            // TODO: return appropriate mock package type

            // Assemble response here
            byte[] response = MockResponses.a(requestPacket).to_bytes();

            // Build UDP packet
            DatagramPacket responsePacket = new DatagramPacket(
                    response,
                    response.length,
                    clientAddress,
                    clientPort
            );

            // Actually send the packet over our socket
            socket.send(responsePacket);

            System.out.println("Send response to: " + clientAddress + ":" + clientPort);
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
