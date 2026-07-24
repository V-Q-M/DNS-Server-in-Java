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
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Serves statistics for the dashboard frontend using multiple endpoints. Each endpoint runs a different SQL query and
 * returns the results as JSON
 */
public class DashboardQueriesHandler implements HttpHandler {

    private static final List<String> columnNames = List.of(
            "transaction_id",
            "received_at",
            "client_ip",
            "client_port",
            "protocol",
            "qname",
            "qtype",
            "rcode",
            "response_source",
            "responded_at",
            "resolution_trace",
            "latency");
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
        Map<String, String> arguments = parseArguments(exchange.getRequestURI().getQuery());
        Timestamp startTime = Timestamp.from(Instant.EPOCH);
        try {
            startTime = Timestamp.from(Instant.ofEpochSecond(Long.parseLong(arguments.get("start_time"))));
        } catch (Exception _) {
        }
        // get the path from the request to call the correct query method
        String path = exchange.getRequestURI().getPath();
        try {
            Object body =
                    switch (path) {
                        case "/api/stats/overview" -> fetchOverview(startTime);
                        case "/api/stats/rcodes" -> fetchRcodes(startTime);
                        case "/api/stats/protocols" -> fetchProtocols(startTime);
                        case "/api/stats/rr-types" -> fetchRrTypes(startTime);
                        case "/api/stats/sources" -> fetchSources(startTime);
                        case "/api/stats/timeline" -> fetchTimeline(startTime, arguments.get("granularity"));
                        case "/api/stats/requests" ->
                            fetchRequests(
                                    startTime,
                                    arguments.get("page"),
                                    arguments.get("sorting_column"),
                                    arguments);
                        case String s
                        when s.matches("/api/stats/request/.+") -> fetchRequest(path.split("/api/stats/request/")[1]);
                        case "/api/stats/latency-distribution" -> latencyDistribution(startTime, arguments.get("granularity"));
                        case "/api/stats/top-domains" -> topDomains(startTime, arguments.get("granularity"));
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
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 500, new JSONObject().put("error", e.getMessage()));
        } catch (Exception e){
            e.printStackTrace();
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

    private JSONObject fetchRcodes(Timestamp startTime) throws SQLException, ClassNotFoundException {
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

    private JSONObject fetchProtocols(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT protocol, COUNT(*) AS count FROM dashboard WHERE received_at >= ? GROUP BY protocol";
        return fetchCounts(sql, startTime, "protocol");
    }

    private JSONObject fetchRrTypes(Timestamp startTime) throws SQLException, ClassNotFoundException {
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

    private JSONObject fetchSources(Timestamp startTime) throws SQLException, ClassNotFoundException {
        String sql = "SELECT "
                + "response_source,"
                + "COUNT(*) AS count "
                + "FROM dashboard WHERE received_at >= ? GROUP BY response_source";
        return fetchCounts(sql, startTime, "response_source");
    }

    private JSONObject fetchTimeline(Timestamp startTime, String granularityArgument)
            throws SQLException, ClassNotFoundException, IllegalArgumentException {
        if (granularityArgument == null) {
            granularityArgument = "5 minutes";
        } else if (!granularityArgument.matches("\\d+ (microseconds|milliseconds|seconds|minutes|hours|days|weeks)")) {
            throw new IllegalArgumentException(
                    granularityArgument + " Is not in the correct format use: '5 minutes' for example");
        }
        String sql = """
                SELECT COUNT(*) AS count,
                       date_bin(?::interval, responded_at, TIMESTAMP '2001-01-01') AS intervals
                FROM dashboard WHERE received_at >= ? GROUP BY intervals;
                """;
        try (PreparedStatement stmt = provider.getConnection().prepareStatement(sql)) {
            stmt.setString(1, granularityArgument);
            stmt.setTimestamp(2, startTime);
            return fetchCounts(stmt, "intervals");
        }
    }

    private JSONArray fetchRequests(Timestamp startTime, String page, String sortingColumn, Map<String, String> filter)
            throws SQLException, ClassNotFoundException, IllegalArgumentException {
        int pageIndex = 0;
        if (page != null) {
            try {
                pageIndex = Integer.parseInt(page);
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(page + " is not a valid page number");
            }
        }
        if (sortingColumn == null) {
            sortingColumn = "responded_at";
        } else if (!columnNames.contains(sortingColumn)) {
            throw new IllegalArgumentException(sortingColumn + " is not one of " + String.join(", ", columnNames));
        }
        if(sortingColumn.equals("latency")) {
            sortingColumn = "responded_at-received_at";
        }
        String sql = """
                SELECT *
                FROM dashboard
                WHERE received_at >= ?
                ORDER BY ?
                LIMIT 50
                OFFSET ?
                """;
        try (PreparedStatement stmt = provider.getConnection().prepareStatement(sql)) {
            stmt.setTimestamp(1, startTime);
            stmt.setString(2, sortingColumn);
            stmt.setInt(3, pageIndex * 50);
            ResultSet rs = stmt.executeQuery();
            JSONArray result = new JSONArray();
            while (rs.next()) {
                result.put(buildRequest(rs));
            }
            return result;
        }
    }

    private JSONObject fetchRequest(String transaction_id)
            throws SQLException, ClassNotFoundException, IllegalArgumentException {
        String sql = """
                SELECT *
                FROM dashboard
                WHERE transaction_id = ?
                """;
        try (PreparedStatement stmt = provider.getConnection().prepareStatement(sql)) {
            stmt.setString(1, transaction_id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return buildRequest(rs);
            } else {
                throw new IllegalArgumentException(transaction_id + " does not exist");
            }
        }
    }
    private JSONObject latencyDistribution(Timestamp startTime, String granularityArgument)
            throws SQLException, ClassNotFoundException, IllegalArgumentException {
        System.out.println("here" + granularityArgument);
        float granularity = 10;
        if (granularityArgument == null) {
        } else if (granularityArgument.matches("\\d+ (microseconds|milliseconds|seconds|minutes)")) {
            switch (granularityArgument.split(" ")[1]) {
                case "microseconds":
                    granularity = Integer.parseInt(granularityArgument.split(" ")[0])/1000F;
                    break;
                case "milliseconds":
                    granularity = Integer.parseInt(granularityArgument.split(" ")[0]);
                    break;
                case "seconds":
                    granularity = Integer.parseInt(granularityArgument.split(" ")[0])*1000;
                    break;
                case "minutes":
                    granularity = Integer.parseInt(granularityArgument.split(" ")[0])*1000*1000;
                    break;
                default:
                    break;
            }
        } else {
            throw new IllegalArgumentException(
                    granularityArgument + " Is not in the correct format use: '5 minutes' for example");
        }
        String sql = """
                SELECT COUNT(*) AS count,
                       floor((EXTRACT(epoch FROM (responded_at-received_at))*1000)/?) * ? AS intervals
                FROM dashboard WHERE received_at >= ? GROUP BY intervals;
                """;
        try (PreparedStatement stmt = provider.getConnection().prepareStatement(sql)) {
            stmt.setFloat(1, granularity);
            stmt.setFloat(2, granularity);
            stmt.setTimestamp(3, startTime);
            return fetchCounts(stmt, "intervals");
        }
    }
    private JSONObject topDomains(Timestamp startTime, String granularityArgument)
            throws SQLException, ClassNotFoundException, IllegalArgumentException {
        int granularity;
        if (granularityArgument == null) {
            granularity = 2;
        } else {
            try{
                granularity = Integer.parseInt(granularityArgument);
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        granularityArgument + " Is not in the correct format use: '5 minutes' for example");
            }
        }
        String sql = """
                SELECT COUNT(*) AS count,
                       array_to_string(array_reverse((array_reverse(regexp_split_to_array(qname, E'\\\\.')))[2:?]), '.') AS intervals
                FROM dashboard WHERE received_at >= ? GROUP BY intervals;
                """;
        try (PreparedStatement stmt = provider.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, granularity+2);
            stmt.setTimestamp(2, startTime);
            return fetchCounts(stmt, "intervals");
        }
    }

    private static JSONObject buildRequest(ResultSet rs) throws SQLException {
        return new JSONObject()
                .put("transaction_id", rs.getString("transaction_id"))
                .put("received_at", rs.getTimestamp("received_at"))
                .put("client_ip", rs.getString("client_ip"))
                .put("client_port", rs.getInt("client_port"))
                .put("protocol", rs.getString("protocol"))
                .put("qname", rs.getString("qname"))
                .put("qtype", rs.getInt("qtype"))
                .put("rcode", rs.getInt("rcode"))
                .put("response_source", rs.getString("response_source"))
                .put("responded_at", rs.getTimestamp("responded_at"))
                .put("resolution_trace", rs.getString("resolution_trace"));
    }
    /** Helper method to execute a SQL query that returns counts by label and return the results as JSON */
    private JSONObject fetchCounts(String sql, Timestamp startTime, String labelColumn)
            throws SQLException, ClassNotFoundException {
        try (Connection conn = provider.newConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, startTime);
            return fetchCounts(stmt, labelColumn);
        }
    }

    private JSONObject fetchCounts(PreparedStatement stmt, String labelColumn)
            throws SQLException, ClassNotFoundException {
        try (ResultSet rs = stmt.executeQuery()) {
            JSONObject results = new JSONObject();
            while (rs.next()) {
                results.put(rs.getString(labelColumn), rs.getLong("count"));
            }
            return results;
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

    private static Map<String, String> parseArguments(String args) {
        HashMap<String, String> result = new HashMap<>();
        try {
            for (String argument : args.split("&")) {
                String[] mapping = argument.split("=");
                result.put(mapping[0], mapping[1]);
            }
        } catch (IndexOutOfBoundsException | NullPointerException _) {
            return Map.of();
        }
        return result;
    }
}
