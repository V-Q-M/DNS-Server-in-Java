package io.gitlab.teamproject2026.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;

public class CacheHandler {
    private final Connection conn;
    
    private static final String SQL_A = "INSERT INTO a_rdata (name, rrtype, rrclass, ttl, rdlength, ipv4) VALUES (?, 'A', 'IN', ?, ?, ?)";
    private static final String SQL_AAAA = "INSERT INTO aaaa_rdata (name, rrtype, rrclass, ttl, rdlength, ipv6) VALUES (?, 'AAAA', 'IN', ?, ?, ?)";
    private static final String SQL_CNAME = "INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname) VALUES (?, 'CNAME', 'IN', ?, ?, ?)";
    private static final String SQL_MX = "INSERT INTO mx_rdata (name, rrtype, rrclass, ttl, rdlength, pref, exchange) VALUES (?, 'MX', 'IN', ?, ?, ?, ?)";
    private static final String SQL_NS = "INSERT INTO ns_rdata (name, rrtype, rrclass, ttl, rdlength, nsdname) VALUES (?, 'NS', 'IN', ?, ?, ?)";
    private static final String SQL_PTR = "INSERT INTO ptr_rdata (name, rrtype, rrclass, ttl, rdlength, ptrdname) VALUES (?, 'PTR', 'IN', ?, ?, ?)";
    private static final String SQL_SOA = "INSERT INTO soa_rdata (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum) VALUES (?, 'SOA', 'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_TXT = "INSERT INTO txt_rdata (name, rrtype, rrclass, ttl, rdlength, txtdata) VALUES (?, 'TXT', 'IN', ?, ?, ?)";

    private static final String DEL_A     = "DELETE FROM a_rdata WHERE name = ?";
    private static final String DEL_AAAA  = "DELETE FROM aaaa_rdata WHERE name = ?";
    private static final String DEL_CNAME = "DELETE FROM cname_rdata WHERE name = ?";
    private static final String DEL_MX    = "DELETE FROM mx_rdata WHERE name = ?";
    private static final String DEL_NS    = "DELETE FROM ns_rdata WHERE name = ?";
    private static final String DEL_PTR   = "DELETE FROM ptr_rdata WHERE name = ?";
    private static final String DEL_SOA   = "DELETE FROM soa_rdata WHERE name = ?";
    private static final String DEL_TXT   = "DELETE FROM txt_rdata WHERE name = ?";

    private static final String[] ALL_RDATA_TABLES = {
        "a_rdata", "aaaa_rdata", "cname_rdata", "mx_rdata", "ns_rdata", "ptr_rdata", "soa_rdata", "txt_rdata"
    };

    @FunctionalInterface
    private interface RDataRowMapper {
        RData map(ResultSet rs) throws SQLException;
    }

    public CacheHandler(CacheConnectionProvider provider) throws SQLException, ClassNotFoundException {
        this.conn = provider.getConnection();
    }

    private ResourceRecord[] lookupType(String name, String sql, RRType type, RDataRowMapper mapper) throws SQLException {
        List<ResourceRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceRecord rr = new ResourceRecord();
                    rr.setName(name.split("\\."));
                    rr.setType(type);
                    rr.setRrclass(RRClass.IN);
                    rr.setTtl(rs.getLong("ttl"));
                    rr.setRdata(mapper.map(rs));
                    records.add(rr);
                }
            }
        }
        return records.toArray(new ResourceRecord[0]);
    }

    /**
     * Returns the full ResourceRecord from the Database, given a name and type
     *
     * @param name
     * @param type
     * @return
     * @throws SQLException
     */
    public ResourceRecord[] lookup(String name, RRType type) throws SQLException {
        name = name.toLowerCase();
        switch (type) {
            case A:
                return lookupType(name, "SELECT * FROM a_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.A, rs -> {
                    RDataA rdata = new RDataA(); rdata.setAddress(rs.getBytes("ipv4")); return rdata;
                });
            case AAAA:
                return lookupType(name, "SELECT * FROM aaaa_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.AAAA, rs -> {
                    RDataAAAA rdata = new RDataAAAA(); rdata.setAddress(rs.getBytes("ipv6")); return rdata;
                });
            case CNAME:
                return lookupType(name, "SELECT * FROM cname_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.CNAME, rs -> {
                    RDataCNAME rdata = new RDataCNAME(); rdata.setCname(rs.getString("cname").split("\\.")); return rdata;
                });
            case MX:
                return lookupType(name, "SELECT * FROM mx_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.MX, rs -> {
                    RDataMX rdata = new RDataMX();
                    rdata.setPreference(rs.getInt("pref"));
                    rdata.setExchange(rs.getString("exchange").split("\\."));
                    return rdata;
                });
            case NS:
                return lookupType(name, "SELECT * FROM ns_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.NS, rs -> {
                    RDataNS rdata = new RDataNS(); rdata.setNsdname(rs.getString("nsdname").split("\\.")); return rdata;
                });
            case PTR:
                return lookupType(name, "SELECT * FROM ptr_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.PTR, rs -> {
                    RDataPTR rdata = new RDataPTR(); rdata.setPtrdname(rs.getString("ptrdname").split("\\.")); return rdata;
                });
            case SOA:
                return lookupType(name, "SELECT * FROM soa_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.SOA, rs -> {
                    RDataSOA rdata = new RDataSOA();
                    rdata.setMname(rs.getString("mname").split("\\."));
                    rdata.setRname(rs.getString("rname").split("\\."));
                    rdata.setSerial(rs.getLong("serial"));
                    rdata.setRefresh(rs.getLong("refresh"));
                    rdata.setRetry(rs.getLong("retry"));
                    rdata.setExpire(rs.getLong("expire"));
                    rdata.setMinimum(rs.getLong("minimum"));
                    return rdata;
                });
            case TXT:
                return lookupType(name, "SELECT * FROM txt_rdata WHERE name = ? AND now() < inserted_at + (ttl * interval '1 second')", RRType.TXT, rs -> {
                    RDataTXT rdata = new RDataTXT(); rdata.setTxtdata(rs.getString("txtdata")); return rdata;
                });
            default:
                return new ResourceRecord[0];
        }
    }

    

    private void fillA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setBytes(4, ((RDataA) r.getRdata()).getAddress());
    }

    private void fillAAAA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setBytes(4, ((RDataAAAA) r.getRdata()).getAddress());
    }

    private void fillCNAME(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setString(4, String.join(".", ((RDataCNAME) r.getRdata()).getCname()));
    }

    private void fillMX(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataMX rdata = (RDataMX) r.getRdata();
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setInt(4, rdata.getPreference());
        stmt.setString(5, String.join(".", rdata.getExchange()));
    }

    private void fillNS(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setString(4, String.join(".", ((RDataNS) r.getRdata()).getNsdname()));
    }

    private void fillPTR(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setString(4, String.join(".", ((RDataPTR) r.getRdata()).getPtrdname()));
    }

    private void fillSOA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataSOA rdata = (RDataSOA) r.getRdata();
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setString(4, String.join(".", rdata.getMname()));
        stmt.setString(5, String.join(".", rdata.getRname()));
        stmt.setLong(6, rdata.getSerial());
        stmt.setLong(7, rdata.getRefresh());
        stmt.setLong(8, rdata.getRetry());
        stmt.setLong(9, rdata.getExpire());
        stmt.setLong(10, rdata.getMinimum());
    }

    private void fillTXT(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()).toLowerCase());
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
        stmt.setString(4, ((RDataTXT) r.getRdata()).getTxtdata());
    }

    /**
     * Inserts a given ResourceRecord into the Database
     * 
     * @param record
     * @throws SQLException
     */
    public void insert(ResourceRecord record) throws SQLException {
        delete(String.join(".", record.getName()).toLowerCase(), record.getType());
        switch (record.getType()) {
            case A:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_A)) {
                    fillA(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case AAAA:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_AAAA)) {
                    fillAAAA(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case CNAME:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_CNAME)) {
                    fillCNAME(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case MX:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_MX)) {
                    fillMX(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case NS:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_NS)) {
                    fillNS(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case PTR:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_PTR)) {
                    fillPTR(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case SOA:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_SOA)) {
                    fillSOA(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            case TXT:
                try (PreparedStatement stmt = conn.prepareStatement(SQL_TXT)) {
                    fillTXT(stmt, record);
                    stmt.executeUpdate();
                }
                break;
            default:
                break;
        }
    }

    /**
     * Deletes a ResourceRecord of given name and type
     * 
     * @param name
     * @param type
     * @throws SQLException
     */
    public void delete(String name, RRType type) throws SQLException {
        name = name.toLowerCase();
        String table;
        switch (type) {
            case A:
                table = "a_rdata";
                break;
            case AAAA:
                table = "aaaa_rdata";
                break;
            case CNAME:
                table = "cname_rdata";
                break;
            case MX:
                table = "mx_rdata";
                break;
            case NS:
                table = "ns_rdata";
                break;
            case PTR:
                table = "ptr_rdata";
                break;
            case SOA:
                table = "soa_rdata";
                break;
            case TXT:
                table = "txt_rdata";
                break;
            default:
                return;
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE name = ?")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    /**
     * Clears the database tables of all entries
     * 
     * @throws SQLException
     */
    public void clearAll() throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE TABLE a_rdata, aaaa_rdata, cname_rdata, mx_rdata, ns_rdata, ptr_rdata, soa_rdata, txt_rdata");
        }
    }

    public void deleteExpired() throws SQLException {
        for (String table : ALL_RDATA_TABLES) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM " + table + " WHERE now() >= inserted_at + (ttl * interval '1 second')")) {
                stmt.executeUpdate();
            }
        }
    }

    private void deleteGroupNames(String deleteSql, List<ResourceRecord> group) throws SQLException {
        Set<String> names = new HashSet<>();
        for (ResourceRecord r : group) names.add(String.join(".", r.getName()).toLowerCase());
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            for (String name : names) { stmt.setString(1, name); stmt.addBatch(); }
            stmt.executeBatch();
        }
    }

    /**
     * Inserts a given array of ResourceRecords into the database using per-type
     * batching.
     * 
     * @param records
     * @throws SQLException
     */
    public void insertAll(ResourceRecord[] records) throws SQLException {
        Map<RRType, List<ResourceRecord>> byType = new EnumMap<>(RRType.class);
        for (ResourceRecord record : records) {
            byType.computeIfAbsent(record.getType(), k -> new ArrayList<>()).add(record);
        }

        conn.setAutoCommit(false);
        try {
            List<ResourceRecord> group;

            group = byType.getOrDefault(RRType.A, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_A, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_A)) {
                    for (ResourceRecord r : group) { fillA(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.AAAA, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_AAAA, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_AAAA)) {
                    for (ResourceRecord r : group) { fillAAAA(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.CNAME, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_CNAME, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_CNAME)) {
                    for (ResourceRecord r : group) { fillCNAME(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.MX, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_MX, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_MX)) {
                    for (ResourceRecord r : group) { fillMX(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.NS, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_NS, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_NS)) {
                    for (ResourceRecord r : group) { fillNS(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.PTR, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_PTR, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_PTR)) {
                    for (ResourceRecord r : group) { fillPTR(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.SOA, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_SOA, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_SOA)) {
                    for (ResourceRecord r : group) { fillSOA(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            group = byType.getOrDefault(RRType.TXT, List.of());
            if (!group.isEmpty()) {
                deleteGroupNames(DEL_TXT, group);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_TXT)) {
                    for (ResourceRecord r : group) { fillTXT(stmt, r); stmt.addBatch(); }
                    stmt.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    

    
}
