package io.gitlab.teamproject2026.server;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.DNS_SERVER;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.database.DashboardHandler;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TCPServer extends AbstractServer {
    private final ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
            8, 8, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.AbortPolicy());
    private ServerSocket serverSocket;
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
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (IOException e) {
                    if (!running || serverSocket.isClosed()) {
                        logger.log(
                                (YELLOW + "[SERVER] TCP server stopped on " + bindAddress + ":" + portNumber + RESET),
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.NOTICE,
                                true);
                        break;
                    }
                    throw e;
                }

                try {
                    workerPool.submit(() -> handleTCPClient(clientSocket));
                } catch (RejectedExecutionException e) {
                    try {
                        if (clientSocket != null && !clientSocket.isClosed()) {
                            clientSocket.close();
                        }
                    } catch (IOException closeException) {
                        logger.log(
                                YELLOW + "[SERVER] Failed to close rejected TCP client socket: "
                                        + closeException.getMessage() + RESET,
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.WARNING,
                                true);
                    }
                    if (running) {
                        logger.log(
                                RED + "[SERVER-ERROR] TCP worker pool rejected client " + clientSocket.getInetAddress()
                                        + ":" + clientSocket.getPort() + RESET,
                                LoggingCategoryType.DEFAULT,
                                LoggingSeverity.ERROR,
                                true);
                    }
                }
                logger.log(
                        "[SERVER] TCP pool active=" + workerPool.getActiveCount()
                                + ", poolSize=" + workerPool.getPoolSize()
                                + ", queued=" + workerPool.getQueue().size()
                                + ", completed=" + workerPool.getCompletedTaskCount(),
                        LoggingCategoryType.GENERAL,
                        LoggingSeverity.NOTICE,
                        true);
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

    private void handleTCPClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
                DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            InetAddress clientAddress = socket.getInetAddress();
            int clientPort = socket.getPort();
            socket.setSoTimeout(5_000);

            int length = input.readUnsignedShort();
            byte[] data = input.readNBytes(length);

            if (data.length != length) {
                logger.log(
                        (RED + "[SERVER-ERROR] Incomplete DNS query from " + clientAddress + ":" + clientPort + RESET),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true);
                return;
            }

            dashboardHandler.storeIncomingQuery(data, clientAddress, clientPort, DashboardHandler.Protocol.TCP);

            logger.log(
                    (BLUE + "[SERVER] Received TCP packet from " + clientAddress + ":" + clientPort + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.NOTICE,
                    true);

            // Uncomment this when debugging.
            // logger.log( ("[SERVER] IN: " + DNSParser.toHex(data)), LoggingCategoryType.GENERAL, LoggingSeverity.INFO,
            // true);

            byte[] response = handleQuery(data);

            if (response.length > 65535) {
                logger.log(
                        (RED + "[SERVER-ERROR] TCP DNS response too large" + RESET),
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.ERROR,
                        true);
                return;
            }
            output.writeShort(response.length);
            output.write(response);
            output.flush();

            logger.log(
                    (BLUE + "[SERVER] Sent TCP response to " + clientAddress + ":" + clientPort + RESET),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.NOTICE,
                    true);

            // Uncomment this when debugging.
            // logger.log( ("[SERVER] OUT: " + DNSParser.toHex(response)), LoggingCategoryType.GENERAL,
            // LoggingSeverity.INFO, true);

        } catch (IOException e) {
            if (running) {
                logger.log(
                        RED + "[SERVER-ERROR] Failed to handle TCP client: " + e.getMessage() + RESET,
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.ERROR,
                        true);
            }
        } catch (Exception e) {
            logger.log(
                    RED + "[SERVER-ERROR] Failed to process TCP query: " + e.getMessage() + RESET,
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.CRITICAL,
                    true);
        }
    }

    public void shutdown() {
        running = false;
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

        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.log(
                        YELLOW + "[SERVER] TCP worker pool did not terminate cleanly" + RESET,
                        LoggingCategoryType.DEFAULT,
                        LoggingSeverity.WARNING,
                        true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
