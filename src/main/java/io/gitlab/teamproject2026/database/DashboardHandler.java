package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;
import java.net.InetAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardHandler {
    private final Connection conn;

    // use a concurrent hashmap to support multiple incoming queries simultaneously
    // normal hashmap caused problems
    private final Map<String, DashboardEntry> queries = new ConcurrentHashMap<>();

    public DashboardHandler(Connection conn) {
        this.conn = conn;
    }

    private static final String SQL_ENTRY_INSERT =
            "INSERT INTO dashboard (transaction_id, received_at, client_ip, client_port, protocol, qname, qtype, rcode, response_source, responded_at, resolution_trace) VALUES (?, ?, ?::INET, ?, ?::protocol, ?, ?, ?, ?::ResponseSourceEnum, ?, ?)";

    public DashboardHandler(DatabaseConnectionProvider provider) throws SQLException, ClassNotFoundException {
        // use a new connection to avoid interfering with the cache handler connection,
        // which caused some issues in the past
        this.conn = provider.newConnection();
    }

    public class DashboardEntry {
        String transactionID;
        LocalDateTime receivedAt;
        InetAddress clientIP;
        int clientPort;
        Protocol protocol;
        String qName;
        int qType;
        int rCode;
        ResponseOrigin responseSource;
        LocalDateTime respondedAt;
        String resolutionTrace;

        public DashboardEntry(
                String transactionID,
                LocalDateTime receivedAt,
                InetAddress clientIP,
                int clientPort,
                Protocol protocol) {
            this.transactionID = transactionID;
            this.receivedAt = receivedAt;
            this.clientIP = clientIP;
            this.clientPort = clientPort;
            this.protocol = protocol;
        }
    }

    public enum ResponseOrigin {
        ZONE,
        CACHE,
        FORWARDER,
        RECURSIVE_RESOLVER,
        STUB;
    }

    public enum Protocol {
        TCP,
        UDP
    }

    /**
     * Stores partial information of an incoming query to be later written to the database once completed.
     *
     * @param data the received databytes used for temporary identification until the real id has been established
     * @param clientIP ip of the querying client
     * @param clientPort port of the querying client
     * @param protocol the protocol used to interact wit the client, depends on the server receiving the query (TCP,
     *     UDP)
     */
    public void storeIncomingQuery(byte[] data, InetAddress clientIP, int clientPort, Protocol protocol) {
        String transactionID = "" + Arrays.hashCode(data);
        queries.put(
                transactionID, new DashboardEntry(transactionID, LocalDateTime.now(), clientIP, clientPort, protocol));
    }

    public void storeQueryData(byte[] data, String transactionID, String qname, int qType) {
        // query was previously stored under its hash, as the PacketId was not parsed
        String temporaryID = "" + Arrays.hashCode(data);
        DashboardEntry entry = queries.remove(temporaryID);
        if (entry == null) {
            System.err.println("Dashboard: no incoming entry found for query hash " + temporaryID);
            return;
        }
        entry.transactionID = transactionID;
        entry.qName = qname;
        entry.qType = qType;
        queries.put(transactionID, entry);
    }

    public void storeResponseData(
            String transactionID, int rCode, ResponseOrigin responseSource, String resolutionTrace) {
        DashboardEntry entry = queries.get(transactionID);
        if (entry == null) {
            System.err.println("Dashboard: no entry found for transaction id " + transactionID);
            return;
        }
        entry.rCode = rCode;
        entry.responseSource = responseSource;
        entry.resolutionTrace = resolutionTrace;
    }

    public void responseSent(String transactionID) {
        DashboardEntry entry = queries.remove(transactionID);
        if (entry == null) {
            System.err.println("Dashboard: no entry found for transaction id " + transactionID);
            return;
        }
        try {
            entry.respondedAt = LocalDateTime.now();
            storeToDatabase(entry);
        } catch (SQLException e) {
            System.err.println("Failed to store dashboard entry: " + e.getMessage());
        }
    }

    private void storeToDatabase(DashboardEntry entry) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(SQL_ENTRY_INSERT);
        stmt.setString(1, entry.transactionID);
        stmt.setTimestamp(2, Timestamp.valueOf(entry.receivedAt));
        stmt.setString(3, entry.clientIP.getHostAddress());
        stmt.setInt(4, entry.clientPort);
        stmt.setString(5, entry.protocol.toString());
        stmt.setString(6, entry.qName);
        stmt.setInt(7, entry.qType);
        stmt.setInt(8, entry.rCode);
        if (entry.responseSource != null) {
            stmt.setString(9, entry.responseSource.toString());
        } else {
            stmt.setNull(9, Types.VARCHAR);
        }
        stmt.setTimestamp(10, Timestamp.valueOf(entry.respondedAt));
        stmt.setNull(11, Types.OTHER);
        stmt.execute();
        System.out.println("[DASHBOARD] Stored entry " + entry.transactionID + " (" + entry.qName + ") to database");
    }
}
