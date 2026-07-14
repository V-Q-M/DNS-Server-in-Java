package io.gitlab.teamproject2026.server;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.DNS_SERVER;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.model.DNSPacket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.*;

public class UDPServer extends AbstractServer {
    private static final int UDP_MAX_PAYLOAD_SIZE = 65507; // see https://en.wikipedia.org/wiki/User_Datagram_Protocol
    private final ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
            8, 8, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.AbortPolicy());

    private DatagramSocket socket;
    private volatile boolean running = true;

    public UDPServer(ServerContext context, InetAddress bindAddress, int portNumber) {
        super(context, bindAddress, portNumber);
    }

    @Override
    public void run() {
        handleUDP();
    }

    private void handleUDP() {
        // Open UDP socket
        try {
            socket = new DatagramSocket(portNumber, bindAddress);
            logger.log(
                    (GREEN + "[SERVER] UDP server listening on " + bindAddress.toString() + ":" + portNumber + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.NOTICE,
                    true);

            // Buffer necessary to store the packet
            byte[] buffer = new byte[UDP_MAX_PAYLOAD_SIZE];

            // Server loop
            while (running && !Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    if (!running || socket.isClosed()) {
                        logger.log(
                                (YELLOW + "[SERVER] UDP server stopped on " + bindAddress + ":" + portNumber + RESET),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.NOTICE,
                                true);
                        break;
                    }
                    throw e;
                }
                // Received DNS packet is stored here
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                // Store the client IP and client Port for further use
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                // Handle the query and receive a byte array as response
                try {
                    workerPool.submit(() -> handleUDPQueryPacket(data, clientAddress, clientPort));
                } catch (RejectedExecutionException e) {
                    if (running) {
                        logger.log(
                                RED + "[SERVER-ERROR] UDP worker pool rejected query from " + clientAddress + ":"
                                        + clientPort + RESET,
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR,
                                true);
                    }
                }
                logger.log(
                        "[SERVER] UDP pool active=" + workerPool.getActiveCount()
                                + ", poolSize=" + workerPool.getPoolSize()
                                + ", queued=" + workerPool.getQueue().size()
                                + ", completed=" + workerPool.getCompletedTaskCount(),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.NOTICE,
                        true);
            }
        } catch (IOException e) {
            if (running) {
                logger.log(
                        RED + "[SERVER-ERROR] No permission to use port " + portNumber + RESET,
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.CRITICAL,
                        true);
                DNS_SERVER.shutdown();
            }
        }
    }

    private void handleUDPQueryPacket(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            logger.log(
                    (BLUE + "[SERVER] Received UDP packet from " + clientAddress + ":" + clientPort + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.NOTICE,
                    true);

            // Uncomment this when debugging
            // Print the hex dump. The Bytes are separated by space e.g. "F3 25 01..."
            // logger.log( ("[SERVER] IN: " + DNSParser.toHex(data)), LoggingCategoryType.GENERAL, LoggingSeverity.INFO,
            // true);
            byte[] byteResponse = handleUDPQuery(data);

            // Build UDP packet
            DatagramPacket responsePacket =
                    new DatagramPacket(byteResponse, byteResponse.length, clientAddress, clientPort);

            // Actually send the packet over our socket
            socket.send(responsePacket);

            logger.log(
                    (BLUE + "[SERVER] Send UDP response to: " + clientAddress + ":" + clientPort + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.NOTICE,
                    true);

            // Uncomment this for debugging
            // logger.log( ("[SERVER] OUT: " + DNSParser.toHex(byteResponse)), LoggingCategoryType.GENERAL,
            // LoggingSeverity.INFO, true);
        } catch (IOException e) {
            if (running) {
                logger.log(
                        RED + "[SERVER-ERROR] Failed to send UDP response to " + clientAddress + ":" + clientPort
                                + " - " + e.getMessage() + RESET,
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.CRITICAL,
                        true);
            }
        } catch (Exception e) {
            logger.log(
                    RED + "[SERVER-ERROR] Failed to handle UDP query from " + clientAddress + ":" + clientPort + " - "
                            + e.getMessage() + RESET,
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.CRITICAL,
                    true);
        }
    }

    /*
    Specialized version of handleQuery which also checks for truncated response
     */
    public byte[] handleUDPQuery(byte[] data) throws IllegalArgumentException {
        DNSPacket query;
        try {
            query = DNSPacket.fromBytes(data);
        } catch (IllegalArgumentException e) {
            byte[] formErr = buildFormError(data);
            logger.log(
                    RED + "[SERVER-ERROR] Failed to handle UDP query, returning Form Error" + e.getMessage() + RESET,
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.CRITICAL,
                    true);
            return formErr;
        }

        // Set 512 Bytes as the default limit for UDP responses
        int effectiveLimit = 512;

        // If the query uses EDNS, use the UDP size specified in the OPT record
        // Must not be larger than the server UDP size limit defined in the server config
        if (query.getEdnsRecord() != null) {
            effectiveLimit =
                    Math.max(512, Math.min(query.getEdnsRecord().getRequestedPayloadSize(), this.ednsUdpSizeLimit));
        }

        logger.log("[SERVER] UDP-SIZE: " + effectiveLimit, LoggingCategoryType.GENERAL, LoggingSeverity.DEBUG, true);

        byte[] result = handleQuery(data);
        if (result.length <= effectiveLimit) {
            return result;
        }

        DNSPacket response = DNSPacket.fromBytes(result);

        return responseBuilder.truncatedUDPResponse(query, response.isAa()).toBytes();
    }

    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.log(
                        YELLOW + "[SERVER] UDP worker pool did not terminate cleanly" + RESET,
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.WARNING,
                        true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
