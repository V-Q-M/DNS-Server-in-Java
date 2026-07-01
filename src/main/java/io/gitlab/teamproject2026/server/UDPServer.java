package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.model.DNSPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import static io.gitlab.teamproject2026.AnsiColors.*;

public class UDPServer extends AbstractServer {
    private static final int UDP_MAX_PAYLOAD_SIZE = 65507; // see https://en.wikipedia.org/wiki/User_Datagram_Protocol

    public UDPServer(ServerContext context){
        super(context);
    }

    @Override
    public void run() {
        handleUDP();
    }

    private void handleUDP(){
        // Open UDP socket
        try (DatagramSocket socket = new DatagramSocket(portNumber, bindAddress)) {
            logger.log(
                    (GREEN + "[SERVER] UDP server listening on " + bindAddress.toString() + ":" + portNumber + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.NOTICE,
                    true
            );

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                logger.log(
                        (BLUE + "[SERVER] Received UDP packet from " + clientAddress + ":" + clientPort + RESET),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.NOTICE,
                        true
                );
                // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
                logger.log(
                        ("[SERVER] IN: " + DNSParser.toHex(data)),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.INFO,
                        true
                );

                // Handle the query and receive a byte array as response
                byte[] byteResponse = handleUDPQuery(data);

                // Build UDP packet
                DatagramPacket responsePacket = new DatagramPacket(
                        byteResponse,
                        byteResponse.length,
                        clientAddress,
                        clientPort);

                // Actually send the packet over our socket
                socket.send(responsePacket);

                logger.log(
                        (BLUE + "[SERVER] Send UDP response to: " + clientAddress + ":" + clientPort + RESET),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.NOTICE,
                        true
                );
                logger.log(
                        ("[SERVER] OUT: " + DNSParser.toHex(byteResponse)),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.INFO,
                        true
                );
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    int udpResponseSizeLimit = 512;
    /*
    Specialized version of handleQuery which also checks for truncated response
     */
    public byte[] handleUDPQuery(byte[] data) {
        byte[] result = handleQuery(data);

        if (result.length <= udpResponseSizeLimit) {
            return result;
        }

        DNSPacket query = DNSPacket.fromBytes(data);
        DNSPacket response = DNSPacket.fromBytes(result);

        return responseBuilder.truncatedUDPResponse(query, response.isAa()).toBytes();
    }
}
