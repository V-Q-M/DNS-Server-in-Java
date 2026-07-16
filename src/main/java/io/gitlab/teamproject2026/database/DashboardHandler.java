package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.RData;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.server.DNSPayload;

import java.net.InetAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class DashboardHandler {
    private final Connection conn;

    private final HashMap<String, DashboardEntry> queries = new HashMap<>();
    public DashboardHandler(Connection conn) {
        this.conn = conn;
    }
    private static final String SQL_ENTRY_INSERT =
            "INSERT INTO dashboard (transaction_id, received_at, client_ip, client_port, protocol, qname, qtype, rcode, response_source, responded_at, resolution_trace) VALUES (?, ?, ?::INET, ?, ?, ?, ?, ?, ?, ?::INET, ?)";

    public DashboardHandler(DatabaseConnectionProvider provider) {
        this.conn = provider.getConnection();
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
        public DashboardEntry(String transactionID, LocalDateTime receivedAt, InetAddress clientIP, int clientPort, Protocol protocol) {
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
     * @param data the received databytes used for temporary identification until the real id has been established
     * @param clientIP ip of the querying client
     * @param clientPort port of the querying client
     * @param protocol the protocol used to interact wit the client, depends on the server receiving the query (TCP, UDP)
     */
    public void storeIncomingQuery(byte[] data, InetAddress clientIP, int clientPort, Protocol protocol){
        String transactionID = "" + Arrays.hashCode(data);
        queries.put(transactionID, new DashboardEntry(transactionID, LocalDateTime.now(), clientIP, clientPort, protocol));
    }

    public void storeQueryData(byte[] data, String transactionID, String qname, int qType){
        // query was previously stored under its hash, as the PacketId was not parsed
        String temporaryID = "" + Arrays.hashCode(data);
        DashboardEntry entry = queries.get(temporaryID);
        queries.put(transactionID, entry);
        queries.remove(temporaryID);
        entry.qName = qname;
        entry.rCode = qType;
    }

    public void storeResponseData(String transactionID, int rCode, ResponseOrigin responseSource, String resolutionTrace ){
        DashboardEntry entry = queries.get(transactionID);
        entry.rCode = rCode;
        entry.responseSource = responseSource;
        entry.resolutionTrace = resolutionTrace;
    }

    public void responseSent(String transactionID){
        try {
            DashboardEntry entry = queries.get(transactionID);
            entry.respondedAt = LocalDateTime.now();
            storeToDatabase(entry);
        } catch (SQLException _) {

        }
        queries.remove(transactionID);
    }

    private void storeToDatabase(DashboardEntry entry) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(SQL_ENTRY_INSERT);
        stmt.setString(1, entry.transactionID);
        stmt.setTimestamp(2, Timestamp.valueOf(entry.receivedAt));
        stmt.setString(3, entry.clientIP.toString());
        stmt.setInt(4, entry.clientPort);
        stmt.setString(5, entry.protocol.toString());
        stmt.setString(6, entry.qName);
        stmt.setInt(7, entry.qType);
        stmt.setInt(8, entry.rCode);
        stmt.setString(9, entry.responseSource.toString());
        stmt.setString(10, entry.respondedAt.toString());
        stmt.setString(11, "");
        stmt.execute();
    }
}
