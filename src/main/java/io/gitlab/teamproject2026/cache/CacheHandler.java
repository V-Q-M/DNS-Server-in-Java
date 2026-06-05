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
import java.sql.Timestamp;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;

public class CacheHandler {
    private final Connection conn;

    private static final String SQL_A = "INSERT INTO a_rdata    (name, rrtype, rrclass, ttl, rdlength, ipv4,     expires_at) VALUES (?, 'A',    'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_AAAA = "INSERT INTO aaaa_rdata  (name, rrtype, rrclass, ttl, rdlength, ipv6,     expires_at) VALUES (?, 'AAAA', 'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_CNAME = "INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname,    expires_at) VALUES (?, 'CNAME','IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_MX = "INSERT INTO mx_rdata    (name, rrtype, rrclass, ttl, rdlength, pref, exchange, expires_at) VALUES (?, 'MX',   'IN', ?, ?, ?, ?,       now() + (? * interval '1 second'))";
    private static final String SQL_NS = "INSERT INTO ns_rdata    (name, rrtype, rrclass, ttl, rdlength, nsdname,  expires_at) VALUES (?, 'NS',   'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_PTR = "INSERT INTO ptr_rdata   (name, rrtype, rrclass, ttl, rdlength, ptrdname, expires_at) VALUES (?, 'PTR',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_SOA = "INSERT INTO soa_rdata   (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum, expires_at) VALUES (?, 'SOA',  'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";
    private static final String SQL_TXT = "INSERT INTO txt_rdata   (name, rrtype, rrclass, ttl, rdlength, txtdata,  expires_at) VALUES (?, 'TXT',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";

    private static final String DEL_A = "DELETE FROM a_rdata WHERE LOWER(name) = ?";
    private static final String DEL_AAAA = "DELETE FROM aaaa_rdata WHERE LOWER(name) = ?";
    private static final String DEL_CNAME = "DELETE FROM cname_rdata WHERE LOWER(name) = ?";
    private static final String DEL_MX = "DELETE FROM mx_rdata WHERE LOWER(name) = ?";
    private static final String DEL_NS = "DELETE FROM ns_rdata WHERE LOWER(name) = ?";
    private static final String DEL_PTR = "DELETE FROM ptr_rdata WHERE LOWER(name) = ?";
    private static final String DEL_SOA = "DELETE FROM soa_rdata WHERE LOWER(name) = ?";
    private static final String DEL_TXT = "DELETE FROM txt_rdata WHERE LOWER(name) = ?";

    private static final String[] ALL_RDATA_TABLES = {
            "a_rdata", "aaaa_rdata", "cname_rdata", "mx_rdata", "ns_rdata", "ptr_rdata", "soa_rdata", "txt_rdata"
    };

    @FunctionalInterface
    private interface RDataRowMapper {
        RData map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface StatementFiller {
        void fill(PreparedStatement stmt, ResourceRecord r) throws SQLException;
    }

    public CacheHandler(CacheConnectionProvider provider) throws SQLException, ClassNotFoundException {
        this.conn = provider.getConnection();
    }

    private static long remainingTtlSeconds(Timestamp expiresAt) {
        long seconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(1, seconds);
    }

    private ResourceRecord[] lookupType(String name, String sql, RRType type, RDataRowMapper mapper)
            throws SQLException {
        List<ResourceRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceRecord rr = new ResourceRecord();
                    rr.setName(rs.getString("name").split("\\."));
                    //rr.setType(type);
                    rr.setRrclass(RRClass.IN);
                    rr.setTtl(remainingTtlSeconds(rs.getTimestamp("expires_at")));
                    //rr.setRdlength(rs.getInt("rdlength"));
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
                return lookupType(name,
                        "SELECT * FROM a_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.A, rs -> {
                            RDataA rdata = new RDataA();
                            rdata.setAddress(rs.getBytes("ipv4"));
                            return rdata;
                        });
            case AAAA:
                return lookupType(name,
                        "SELECT * FROM aaaa_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.AAAA, rs -> {
                            RDataAAAA rdata = new RDataAAAA();
                            rdata.setAddress(rs.getBytes("ipv6"));
                            return rdata;
                        });
            case CNAME:
                return lookupType(name,
                        "SELECT * FROM cname_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.CNAME, rs -> {
                            RDataCNAME rdata = new RDataCNAME();
                            rdata.setCname(rs.getString("cname").split("\\."));
                            return rdata;
                        });
            case MX:
                return lookupType(name,
                        "SELECT * FROM mx_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.MX, rs -> {
                            RDataMX rdata = new RDataMX();
                            rdata.setPreference(rs.getInt("pref"));
                            rdata.setExchange(rs.getString("exchange").split("\\."));
                            return rdata;
                        });
            case NS:
                return lookupType(name,
                        "SELECT * FROM ns_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.NS, rs -> {
                            RDataNS rdata = new RDataNS();
                            rdata.setNsdname(rs.getString("nsdname").split("\\."));
                            return rdata;
                        });
            case PTR:
                return lookupType(name,
                        "SELECT * FROM ptr_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.PTR, rs -> {
                            RDataPTR rdata = new RDataPTR();
                            rdata.setPtrdname(rs.getString("ptrdname").split("\\."));
                            return rdata;
                        });
            case SOA:
                return lookupType(name,
                        "SELECT * FROM soa_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.SOA, rs -> {
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
                return lookupType(name,
                        "SELECT * FROM txt_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.TXT, rs -> {
                            RDataTXT rdata = new RDataTXT();
                            rdata.setTxtdata((String[]) rs.getArray("txtdata").getArray());
                            return rdata;
                        });
            default:
                return new ResourceRecord[0];
        }
    }

    /*
     * Helper functions to fill in Record statements
     */

    private void fillCommon(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        stmt.setString(1, String.join(".", r.getName()));
        stmt.setLong(2, r.getTtl());
        stmt.setInt(3, r.getRdlength());
    }

    private void fillA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setBytes(4, ((RDataA) r.getRdata()).getAddress());
        stmt.setLong(5, r.getTtl());
    }

    private void fillAAAA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setBytes(4, ((RDataAAAA) r.getRdata()).getAddress());
        stmt.setLong(5, r.getTtl());
    }

    private void fillCNAME(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setString(4, String.join(".", ((RDataCNAME) r.getRdata()).getCname()));
        stmt.setLong(5, r.getTtl());
    }

    private void fillMX(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataMX rdata = (RDataMX) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getPreference());
        stmt.setString(5, String.join(".", rdata.getExchange()));
        stmt.setLong(6, r.getTtl());
    }

    private void fillNS(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setString(4, String.join(".", ((RDataNS) r.getRdata()).getNsdname()));
        stmt.setLong(5, r.getTtl());
    }

    private void fillPTR(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setString(4, String.join(".", ((RDataPTR) r.getRdata()).getPtrdname()));
        stmt.setLong(5, r.getTtl());
    }

    private void fillSOA(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataSOA rdata = (RDataSOA) r.getRdata();
        fillCommon(stmt, r);
        stmt.setString(4, String.join(".", rdata.getMname()));
        stmt.setString(5, String.join(".", rdata.getRname()));
        stmt.setLong(6, rdata.getSerial());
        stmt.setLong(7, rdata.getRefresh());
        stmt.setLong(8, rdata.getRetry());
        stmt.setLong(9, rdata.getExpire());
        stmt.setLong(10, rdata.getMinimum());
        stmt.setLong(11, r.getTtl());
    }

    private void fillTXT(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        fillCommon(stmt, r);
        stmt.setArray(4, conn.createArrayOf("text", ((RDataTXT) r.getRdata()).getTxtdata()));
        stmt.setLong(5, r.getTtl());
    }

    private void singleInsert(String insertSql, ResourceRecord r, StatementFiller filler) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            filler.fill(stmt, r);
            stmt.executeUpdate();
        }
    }

    /**
     * Inserts a given ResourceRecord into the Database
     *
     * @param record
     * @throws SQLException
     */
    void insert(ResourceRecord record) throws SQLException {
        delete(String.join(".", record.getName()).toLowerCase(), record.getType());
        switch (record.getType()) {
            case A:
                singleInsert(SQL_A, record, this::fillA);
                break;
            case AAAA:
                singleInsert(SQL_AAAA, record, this::fillAAAA);
                break;
            case CNAME:
                singleInsert(SQL_CNAME, record, this::fillCNAME);
                break;
            case MX:
                singleInsert(SQL_MX, record, this::fillMX);
                break;
            case NS:
                singleInsert(SQL_NS, record, this::fillNS);
                break;
            case PTR:
                singleInsert(SQL_PTR, record, this::fillPTR);
                break;
            case SOA:
                singleInsert(SQL_SOA, record, this::fillSOA);
                break;
            case TXT:
                singleInsert(SQL_TXT, record, this::fillTXT);
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
        String sql;
        switch (type) {
            case A:
                sql = DEL_A;
                break;
            case AAAA:
                sql = DEL_AAAA;
                break;
            case CNAME:
                sql = DEL_CNAME;
                break;
            case MX:
                sql = DEL_MX;
                break;
            case NS:
                sql = DEL_NS;
                break;
            case PTR:
                sql = DEL_PTR;
                break;
            case SOA:
                sql = DEL_SOA;
                break;
            case TXT:
                sql = DEL_TXT;
                break;
            default:
                return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    /**
     * Checks the Database tables for expired entries and deletes them
     * 
     * @throws SQLException
     */
    public void deleteExpired() throws SQLException {
        for (String table : ALL_RDATA_TABLES) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM " + table + " WHERE now() >= expires_at")) {
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Batch-deletes all DB entries whose name appears in the group, deduplicated to avoid redundant deletes.
     *
     * @param deleteSql the type-specific DELETE SQL to execute
     * @param group the records whose names should be deleted
     * @throws SQLException
     */
    private void deleteGroupNames(String deleteSql, List<ResourceRecord> group) throws SQLException {
        Set<String> names = new HashSet<>();
        for (ResourceRecord r : group)
            names.add(String.join(".", r.getName()).toLowerCase());
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            for (String name : names) {
                stmt.setString(1, name);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    /**
     * Replaces the stored RRset for each name in the group: deletes existing entries, then batch-inserts the new ones.
     *
     * @param group the records to insert
     * @param deleteSql the type-specific DELETE SQL
     * @param insertSql the type-specific INSERT SQL
     * @param filler fills the INSERT statement parameters for a given record
     * @throws SQLException
     */
    private void batchInsert(List<ResourceRecord> group, String deleteSql, String insertSql, StatementFiller filler)
            throws SQLException {
        if (group.isEmpty())
            return;
        deleteGroupNames(deleteSql, group);
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (ResourceRecord r : group) {
                filler.fill(stmt, r);
                stmt.addBatch();
            }
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
            batchInsert(byType.getOrDefault(RRType.A, List.of()), DEL_A, SQL_A, this::fillA);
            batchInsert(byType.getOrDefault(RRType.AAAA, List.of()), DEL_AAAA, SQL_AAAA, this::fillAAAA);
            batchInsert(byType.getOrDefault(RRType.CNAME, List.of()), DEL_CNAME, SQL_CNAME, this::fillCNAME);
            batchInsert(byType.getOrDefault(RRType.MX, List.of()), DEL_MX, SQL_MX, this::fillMX);
            batchInsert(byType.getOrDefault(RRType.NS, List.of()), DEL_NS, SQL_NS, this::fillNS);
            batchInsert(byType.getOrDefault(RRType.PTR, List.of()), DEL_PTR, SQL_PTR, this::fillPTR);
            batchInsert(byType.getOrDefault(RRType.SOA, List.of()), DEL_SOA, SQL_SOA, this::fillSOA);
            batchInsert(byType.getOrDefault(RRType.TXT, List.of()), DEL_TXT, SQL_TXT, this::fillTXT);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

}
