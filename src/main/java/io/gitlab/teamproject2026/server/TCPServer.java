package io.gitlab.teamproject2026.server;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.DNS_SERVER;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer extends AbstractServer {
    private ServerSocket serverSocket;
    private Socket socket;
    private volatile boolean running = true;

    public TCPServer(ServerContext context, InetAddress bindAddress, int portNumber) {
        super(context, bindAddress, portNumber);
    }

    @Override
    public void run() {
        handleTCP();
    }

    private void handleTCP() {
        // Open TCP socket
        try {
            serverSocket = new ServerSocket(portNumber, 50, bindAddress);
            logger.log(
                    (GREEN + "[SERVER] TCP server listening on " + bindAddress.toString() + ":" + portNumber + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.NOTICE,
                    true);

            // Server loop
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                    InetAddress clientAddress = socket.getInetAddress();
                    int clientPort = socket.getPort();

                    int length = input.readUnsignedShort();
                    byte[] data = input.readNBytes(length);

                    if (data.length != length) {
                        logger.log(
                                (RED + "[SERVER-ERROR] Incomplete DNS query" + RESET),
                                LoggingCategoryType.QUERY_ERRORS,
                                LoggingSeverity.ERROR,
                                true);
                        continue;
                    }

                    logger.log(
                            (BLUE + "[SERVER] Received TCP packet from " + clientAddress + ":" + clientPort + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true);

                    logger.log(
                            ("[SERVER] IN: " + DNSParser.toHex(data)),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.INFO,
                            true);

                    byte[] response = handleQuery(data);

                    if (response.length > 65535) {
                        logger.log(
                                (RED + "[SERVER-ERROR] TCP DNS response too large" + RESET),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR,
                                true);
                        continue;
                    }

                    output.writeShort(response.length);
                    output.write(response);
                    output.flush();

                    logger.log(
                            (BLUE + "[SERVER] Sent TCP response to " + clientAddress + ":" + clientPort + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true);

                    logger.log(
                            ("[SERVER] OUT: " + DNSParser.toHex(response)),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.INFO,
                            true);

                } catch (IOException e) {
                    if (!running || serverSocket.isClosed()) {
                        logger.log(
                                (YELLOW + "[SERVER] TCP server stopped on " + bindAddress + ":" + portNumber + RESET),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.NOTICE,
                                true);
                        break;
                    }

                    logger.log(
                            (RED + "[SERVER-ERROR] Failed to handle TCP client: " + e.getMessage() + RESET),
                            LoggingCategoryType.DEFAULT,
                            LoggingSeverity.ERROR,
                            true);
                }
            }
        } catch (IOException e) {
            logger.log(
                    RED + "[SERVER-ERROR] No permission to use port " + portNumber + RESET,
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.CRITICAL,
                    true);
            DNS_SERVER.shutdown();
        }
    }

    public void shutdown() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.log(
                    (YELLOW + "[SERVER] Failed to close TCP client socket: " + e.getMessage() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.WARNING,
                    true);
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.log(
                    (YELLOW + "[SERVER] Failed to close TCP server socket: " + e.getMessage() + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.WARNING,
                    true);
        }
    }
}
