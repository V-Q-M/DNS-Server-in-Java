package io.gitlab.teamproject2026.dashboard_server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Serves statistics for the dashboard frontend using multiple endpoints.
 * Each endpoint runs a different SQL query and returns the results as JSON
 */
public class DashboardQueriesHandler implements HttpHandler {

    // TODO: parse startTime from the request
    private static final Timestamp HARDCODED_START_TIME = Timestamp.valueOf("2026-01-01 00:00:00");

    private final DatabaseConnectionProvider provider;

    public DashboardQueriesHandler(DatabaseConnectionProvider provider) {
        this.provider = provider;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // Only allow GET requests for now, because the dashboard only uses GET
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Only GET is supported"));
            return;
        }

        // get the path from the request to call the correct query method
        String path = exchange.getRequestURI().getPath();
        try {
            Object body =
                    switch (path) {
                        case "/api/stats/overview" -> fetchOverview(HARDCODED_START_TIME);
                        case "/api/stats/rcodes" -> fetchRcodes(HARDCODED_START_TIME);
                        case "/api/stats/protocols" -> fetchProtocols(HARDCODED_START_TIME);
                        case "/api/stats/rr-types" -> fetchRrTypes(HARDCODED_START_TIME);
                        default -> null;
                    };
            if (body == null) {
                sendJson(exchange, 404, new JSONObject().put("error", "Unknown endpoint"));
                return;
            }
            sendJson(exchange, 200, body);
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Dashboard stats query failed for " + path + ": " + e.getMessage());
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private JSONObject fetchOverview(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT "
                + "COUNT(*) AS total_queries, "
                + "COUNT(*) FILTER (WHERE response_source = 'CACHE') AS cache_hits, "
                + "COUNT(*) FILTER (WHERE response_source != 'CACHE' AND (rcode = 0 OR rcode = 3)) AS cache_misses, "
                + "COUNT(*) FILTER (WHERE rcode IS NULL OR rcode NOT IN (0, 3)) AS errors, "
                + "AVG(EXTRACT(EPOCH FROM (responded_at - received_at)) * 1000) "
                + "  FILTER (WHERE responded_at IS NOT NULL) AS avg_latency_ms, "
                + "COUNT(DISTINCT qname) AS unique_domains "
                + "FROM dashboard WHERE received_at >= ?";

        try (Connection conn = provider.newConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTime);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                JSONObject result = new JSONObject();
                result.put("total_queries", rs.getLong("total_queries"));
                result.put("cache_hits", rs.getLong("cache_hits"));
                result.put("cache_misses", rs.getLong("cache_misses"));
                result.put("errors", rs.getLong("errors"));
                double avgLatencyMs = rs.getDouble("avg_latency_ms");
                result.put("avg_latency_ms", rs.wasNull() ? JSONObject.NULL : avgLatencyMs);
                result.put("unique_domains", rs.getLong("unique_domains"));
                return result;
            }
        }
    }

    private JSONArray fetchRcodes(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT "
                + "CASE "
                + "  WHEN rcode IS NULL THEN 'NO_RESPONSE' "
                + "  WHEN rcode = 0 THEN 'NOERROR' "
                + "  WHEN rcode = 1 THEN 'FORMERR' "
                + "  WHEN rcode = 2 THEN 'SERVFAIL' "
                + "  WHEN rcode = 3 THEN 'NXDOMAIN' "
                + "  WHEN rcode = 4 THEN 'NOTIMP' "
                + "  WHEN rcode = 5 THEN 'REFUSED' "
                + "  ELSE 'OTHER' "
                + "END AS rcode_name, "
                + "COUNT(*) AS count "
                + "FROM dashboard WHERE received_at >= ? GROUP BY 1";
        return fetchCounts(sql, startTime, "rcode_name");
    }

    private JSONArray fetchProtocols(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT protocol, COUNT(*) AS count FROM dashboard WHERE received_at >= ? GROUP BY protocol";
        return fetchCounts(sql, startTime, "protocol");
    }

    private JSONArray fetchRrTypes(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT "
                + "CASE qtype "
                + "  WHEN 1 THEN 'A' WHEN 2 THEN 'NS' WHEN 5 THEN 'CNAME' WHEN 6 THEN 'SOA' "
                + "  WHEN 12 THEN 'PTR' WHEN 15 THEN 'MX' WHEN 16 THEN 'TXT' WHEN 28 THEN 'AAAA' "
                + "  WHEN 33 THEN 'SRV' ELSE 'OTHER' "
                + "END AS rr_type, "
                + "COUNT(*) AS count "
                + "FROM dashboard WHERE received_at >= ? GROUP BY 1";
        return fetchCounts(sql, startTime, "rr_type");
    }

    /** 
     * Helper method to execute a SQL query that returns counts by label and return the results as JSON 
     */
    private JSONArray fetchCounts(String sql, Timestamp startTime, String labelColumn)
            throws SQLException, ClassNotFoundException {
        try (Connection conn = provider.newConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTime);
            try (ResultSet rs = stmt.executeQuery()) {
                JSONArray results = new JSONArray();
                while (rs.next()) {
                    results.put(new JSONObject()
                            .put(labelColumn, rs.getString(labelColumn))
                            .put("count", rs.getLong("count")));
                }
                return results;
            }
        }
    }

    // Helper method to send a JSON response with the given status code and body
    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
