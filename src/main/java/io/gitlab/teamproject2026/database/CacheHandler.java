package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;
import java.sql.*;
import java.util.*;

public class CacheHandler implements AutoCloseable {
    private final Connection conn;

    private static final String SQL_A =
            "INSERT INTO a_rdata    (name, rrtype, rrclass, ttl, rdlength, ipv4,     expires_at) VALUES (?, 'A',    'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_AAAA =
            "INSERT INTO aaaa_rdata  (name, rrtype, rrclass, ttl, rdlength, ipv6,     expires_at) VALUES (?, 'AAAA', 'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_CNAME =
            "INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname,    expires_at) VALUES (?, 'CNAME','IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_MX =
            "INSERT INTO mx_rdata    (name, rrtype, rrclass, ttl, rdlength, pref, exchange, expires_at) VALUES (?, 'MX',   'IN', ?, ?, ?, ?,       now() + (? * interval '1 second'))";
    private static final String SQL_NS =
            "INSERT INTO ns_rdata    (name, rrtype, rrclass, ttl, rdlength, nsdname,  expires_at) VALUES (?, 'NS',   'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_PTR =
            "INSERT INTO ptr_rdata   (name, rrtype, rrclass, ttl, rdlength, ptrdname, expires_at) VALUES (?, 'PTR',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_SOA =
            "INSERT INTO soa_rdata   (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum, expires_at) VALUES (?, 'SOA',  'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";
    private static final String SQL_TXT =
            "INSERT INTO txt_rdata   (name, rrtype, rrclass, ttl, rdlength, txtdata,  expires_at) VALUES (?, 'TXT',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
    private static final String SQL_UNKNOWN =
            "INSERT INTO unknown_rdata (name, rrtype, rrclass, ttl, rdlength, type, data, expires_at) VALUES (?, NULL, 'IN', ?, ?, ?, ?, now() + (? * interval '1 second'))";

    // DNSSEC
    private static final String SQL_DS =
            "INSERT INTO ds_rdata (name, rrtype, rrclass, ttl, rdlength, key_tag, algorithm, digest_type, digest, expires_at) VALUES (?, 'DS', 'IN', ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String SQL_DNSKEY =
            "INSERT INTO dnskey_rdata (name, rrtype, rrclass, ttl, rdlength, flags, protocol, algorithm, publickey, expires_at) VALUES (?, 'DNSKEY', 'IN', ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String SQL_RRSIG =
            "INSERT INTO rrsig_rdata (name, rrtype, rrclass, ttl, rdlength, type_covered, algorithm, labels, original_ttl, expiration, inception, key_tag, signer_name, signature, expires_at) VALUES (?, 'RRSIG', 'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String SQL_NSEC =
            "INSERT INTO nsec_rdata (name, rrtype, rrclass, ttl, rdlength, next_domain_name, type_bit_maps, expires_at) VALUES (?, 'NSEC', 'IN', ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String SQL_NSEC3 =
            "INSERT INTO nsec3_rdata (name, rrtype, rrclass, ttl, rdlength, hash_algorithm, flags, iterations, salt, next_hashed_owner_name, type_bit_maps, expires_at) VALUES (?, 'NSEC3', 'IN', ?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String SQL_NSEC3PARAM =
            "INSERT INTO nsec3param_rdata (name, rrtype, rrclass, ttl, rdlength, hash_algorithm, flags, iterations, salt, expires_at) VALUES (?, 'NSEC3PARAM', 'IN', ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";

    private static final String DEL_A = "DELETE FROM a_rdata WHERE LOWER(name) = ?";
    private static final String DEL_AAAA = "DELETE FROM aaaa_rdata WHERE LOWER(name) = ?";
    private static final String DEL_CNAME = "DELETE FROM cname_rdata WHERE LOWER(name) = ?";
    private static final String DEL_MX = "DELETE FROM mx_rdata WHERE LOWER(name) = ?";
    private static final String DEL_NS = "DELETE FROM ns_rdata WHERE LOWER(name) = ?";
    private static final String DEL_PTR = "DELETE FROM ptr_rdata WHERE LOWER(name) = ?";
    private static final String DEL_SOA = "DELETE FROM soa_rdata WHERE LOWER(name) = ?";
    private static final String DEL_TXT = "DELETE FROM txt_rdata WHERE LOWER(name) = ?";
    private static final String DEL_UNKNOWN = "DELETE FROM unknown_rdata WHERE LOWER(name) = ? AND type = ?";
    private static final String DEL_DNSKEY = "DELETE FROM dnskey_rdata WHERE LOWER(name) = ?";
    private static final String DEL_DS = "DELETE FROM ds_rdata WHERE LOWER(name) = ?";
    private static final String DEL_RRSIG = "DELETE FROM rrsig_rdata WHERE LOWER(name) = ?";
    private static final String DEL_NSEC = "DELETE FROM nsec_rdata WHERE LOWER(name) = ?";
    private static final String DEL_NSEC3 = "DELETE FROM nsec3_rdata WHERE LOWER(name) = ?";
    private static final String DEL_NSEC3PARAM = "DELETE FROM nsec3param_rdata WHERE LOWER(name) = ?";

    private static final String[] ALL_RDATA_TABLES = {
        "a_rdata",
        "aaaa_rdata",
        "cname_rdata",
        "mx_rdata",
        "ns_rdata",
        "ptr_rdata",
        "soa_rdata",
        "txt_rdata",
        "dnskey_rdata",
        "ds_rdata",
        "rrsig_rdata",
        "nsec_rdata",
        "nsec3_rdata",
        "nsec3param_rdata",
        "unknown_rdata"
    };

    @FunctionalInterface
    private interface RDataRowMapper {
        RData map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface StatementFiller {
        void fill(PreparedStatement stmt, ResourceRecord r) throws SQLException;
    }

    public CacheHandler(DatabaseConnectionProvider provider) {
        this.conn = provider.getConnection();
    }

    private static long remainingTtlSeconds(Timestamp expiresAt) {
        long seconds = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(1, seconds);
    }

    private ResourceRecord[] lookupType(String name, String sql, int type, RDataRowMapper mapper) throws SQLException {
        List<ResourceRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceRecord rr = new ResourceRecord();
                    rr.setName(rs.getString("name").split("\\."));
                    // rr.setType(type);
                    rr.setRrclass(RRClass.IN);
                    rr.setTtl(remainingTtlSeconds(rs.getTimestamp("expires_at")));
                    // rr.setRdlength(rs.getInt("rdlength"));
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
    public ResourceRecord[] lookup(String name, int type) throws SQLException {
        name = name.toLowerCase();
        switch (type) {
            case RRType.A:
                return lookupType(
                        name, "SELECT * FROM a_rdata WHERE LOWER(name) = ? AND now() < expires_at", RRType.A, rs -> {
                            RDataA rdata = new RDataA();
                            rdata.setAddress(rs.getBytes("ipv4"));
                            return rdata;
                        });
            case RRType.AAAA:
                return lookupType(
                        name,
                        "SELECT * FROM aaaa_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.AAAA,
                        rs -> {
                            RDataAAAA rdata = new RDataAAAA();
                            rdata.setAddress(rs.getBytes("ipv6"));
                            return rdata;
                        });
            case RRType.CNAME:
                return lookupType(
                        name,
                        "SELECT * FROM cname_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.CNAME,
                        rs -> {
                            RDataCNAME rdata = new RDataCNAME();
                            rdata.setCname(rs.getString("cname").split("\\."));
                            return rdata;
                        });
            case RRType.MX:
                return lookupType(
                        name, "SELECT * FROM mx_rdata WHERE LOWER(name) = ? AND now() < expires_at", RRType.MX, rs -> {
                            RDataMX rdata = new RDataMX();
                            rdata.setPreference(rs.getInt("pref"));
                            rdata.setExchange(rs.getString("exchange").split("\\."));
                            return rdata;
                        });
            case RRType.NS:
                return lookupType(
                        name, "SELECT * FROM ns_rdata WHERE LOWER(name) = ? AND now() < expires_at", RRType.NS, rs -> {
                            RDataNS rdata = new RDataNS();
                            rdata.setNsdname(rs.getString("nsdname").split("\\."));
                            return rdata;
                        });
            case RRType.PTR:
                return lookupType(
                        name,
                        "SELECT * FROM ptr_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.PTR,
                        rs -> {
                            RDataPTR rdata = new RDataPTR();
                            rdata.setPtrdname(rs.getString("ptrdname").split("\\."));
                            return rdata;
                        });
            case RRType.SOA:
                return lookupType(
                        name,
                        "SELECT * FROM soa_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.SOA,
                        rs -> {
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
            case RRType.TXT:
                return lookupType(
                        name,
                        "SELECT * FROM txt_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.TXT,
                        rs -> {
                            RDataTXT rdata = new RDataTXT();
                            rdata.setTxtdata((String[]) rs.getArray("txtdata").getArray());
                            return rdata;
                        });
            case RRType.DS:
                return lookupType(
                        name,
                        "SELECT * FROM ds_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.DS,
                        rs -> new RDataDS(
                                rs.getInt("key_tag"),
                                rs.getInt("algorithm"),
                                rs.getInt("digest_type"),
                                rs.getBytes("digest")));

            case RRType.DNSKEY:
                return lookupType(
                        name,
                        "SELECT * FROM dnskey_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.DNSKEY,
                        rs -> new RDataDNSKEY(
                                rs.getInt("flags"),
                                rs.getInt("protocol"),
                                rs.getInt("algorithm"),
                                rs.getBytes("publickey")));

            case RRType.RRSIG:
                return lookupType(
                        name,
                        "SELECT * FROM rrsig_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.RRSIG,
                        rs -> new RDataRRSIG(
                                rs.getInt("type_covered"),
                                rs.getInt("algorithm"),
                                rs.getInt("labels"),
                                rs.getLong("original_ttl"),
                                rs.getLong("expiration"),
                                rs.getLong("inception"),
                                rs.getInt("key_tag"),
                                rs.getString("signer_name").split("\\."),
                                rs.getBytes("signature")));

            case RRType.NSEC:
                return lookupType(
                        name,
                        "SELECT * FROM nsec_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.NSEC,
                        rs -> new RDataNSEC(
                                rs.getString("next_domain_name").split("\\."), rs.getBytes("type_bit_maps")));

            case RRType.NSEC3:
                return lookupType(
                        name,
                        "SELECT * FROM nsec3_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.NSEC3,
                        rs -> new RDataNSEC3(
                                rs.getInt("hash_algorithm"),
                                rs.getInt("flags"),
                                rs.getInt("iterations"),
                                rs.getBytes("salt"),
                                rs.getBytes("next_hashed_owner_name"),
                                rs.getBytes("type_bit_maps")));

            case RRType.NSEC3PARAM:
                return lookupType(
                        name,
                        "SELECT * FROM nsec3param_rdata WHERE LOWER(name) = ? AND now() < expires_at",
                        RRType.NSEC3PARAM,
                        rs -> new RDataNSEC3PARAM(
                                rs.getInt("hash_algorithm"),
                                rs.getInt("flags"),
                                rs.getInt("iterations"),
                                rs.getBytes("salt")));
            default:
                return lookupType(
                        name,
                        "SELECT * FROM unknown_rdata WHERE LOWER(name) = ? AND type = " + type
                                + " AND now() < expires_at",
                        type,
                        rs -> new RDataUnknown(rs.getInt("type"), rs.getBytes("data")));
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

    private void fillDS(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataDS rdata = (RDataDS) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getKeyTag());
        stmt.setInt(5, rdata.getAlgorithm());
        stmt.setInt(6, rdata.getDigestType());
        stmt.setBytes(7, rdata.getDigest());
        stmt.setLong(8, r.getTtl());
    }

    private void fillDNSKEY(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataDNSKEY rdata = (RDataDNSKEY) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getFlags());
        stmt.setInt(5, rdata.getProtocol());
        stmt.setInt(6, rdata.getAlgorithm());
        stmt.setBytes(7, rdata.getPublickey());
        stmt.setLong(8, r.getTtl());
    }

    private void fillRRSIG(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataRRSIG rdata = (RDataRRSIG) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getTypeCovered());
        stmt.setInt(5, rdata.getAlgorithm());
        stmt.setInt(6, rdata.getLabels());
        stmt.setLong(7, rdata.getOriginalTtl());
        stmt.setLong(8, rdata.getSignatureExpiration());
        stmt.setLong(9, rdata.getSignatureInception());
        stmt.setInt(10, rdata.getKeyTag());
        stmt.setString(11, String.join(".", rdata.getSignerName()));
        stmt.setBytes(12, rdata.getSignature());
        stmt.setLong(13, r.getTtl());
    }

    private void fillNSEC(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataNSEC rdata = (RDataNSEC) r.getRdata();
        fillCommon(stmt, r);
        stmt.setString(4, String.join(".", rdata.getNextDomainName()));
        stmt.setBytes(5, rdata.getTypeBitMaps());
        stmt.setLong(6, r.getTtl());
    }

    private void fillNSEC3(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataNSEC3 rdata = (RDataNSEC3) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getHashAlgorithm());
        stmt.setInt(5, rdata.getFlags());
        stmt.setInt(6, rdata.getIterations());
        stmt.setBytes(7, rdata.getSalt());
        stmt.setBytes(8, rdata.getNextHashedOwnerName());
        stmt.setBytes(9, rdata.getTypeBitMaps());
        stmt.setLong(10, r.getTtl());
    }

    private void fillNSEC3PARAM(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataNSEC3PARAM rdata = (RDataNSEC3PARAM) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getHashAlgorithm());
        stmt.setInt(5, rdata.getFlags());
        stmt.setInt(6, rdata.getIterations());
        stmt.setBytes(7, rdata.getSalt());
        stmt.setLong(8, r.getTtl());
    }

    private void fillUnknown(PreparedStatement stmt, ResourceRecord r) throws SQLException {
        RDataUnknown rdata = (RDataUnknown) r.getRdata();
        fillCommon(stmt, r);
        stmt.setInt(4, rdata.getType());
        stmt.setBytes(5, rdata.toBytes());
        stmt.setLong(6, r.getTtl());
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
            case RRType.A:
                singleInsert(SQL_A, record, this::fillA);
                break;
            case RRType.AAAA:
                singleInsert(SQL_AAAA, record, this::fillAAAA);
                break;
            case RRType.CNAME:
                singleInsert(SQL_CNAME, record, this::fillCNAME);
                break;
            case RRType.MX:
                singleInsert(SQL_MX, record, this::fillMX);
                break;
            case RRType.NS:
                singleInsert(SQL_NS, record, this::fillNS);
                break;
            case RRType.PTR:
                singleInsert(SQL_PTR, record, this::fillPTR);
                break;
            case RRType.SOA:
                singleInsert(SQL_SOA, record, this::fillSOA);
                break;
            case RRType.TXT:
                singleInsert(SQL_TXT, record, this::fillTXT);
                break;
            case RRType.DS:
                singleInsert(SQL_DS, record, this::fillDS);
                break;
            case RRType.DNSKEY:
                singleInsert(SQL_DNSKEY, record, this::fillDNSKEY);
                break;
            case RRType.RRSIG:
                singleInsert(SQL_RRSIG, record, this::fillRRSIG);
                break;
            case RRType.NSEC:
                singleInsert(SQL_NSEC, record, this::fillNSEC);
                break;
            case RRType.NSEC3:
                singleInsert(SQL_NSEC3, record, this::fillNSEC3);
                break;
            case RRType.NSEC3PARAM:
                singleInsert(SQL_NSEC3PARAM, record, this::fillNSEC3PARAM);
                break;
            default:
                if (record.getRdata() instanceof RDataUnknown) {
                    singleInsert(SQL_UNKNOWN, record, this::fillUnknown);
                }
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
    public void delete(String name, int type) throws SQLException {
        name = name.toLowerCase();
        String sql;
        switch (type) {
            case RRType.A:
                sql = DEL_A;
                break;
            case RRType.AAAA:
                sql = DEL_AAAA;
                break;
            case RRType.CNAME:
                sql = DEL_CNAME;
                break;
            case RRType.MX:
                sql = DEL_MX;
                break;
            case RRType.NS:
                sql = DEL_NS;
                break;
            case RRType.PTR:
                sql = DEL_PTR;
                break;
            case RRType.SOA:
                sql = DEL_SOA;
                break;
            case RRType.TXT:
                sql = DEL_TXT;
                break;
            case RRType.DNSKEY:
                sql = DEL_DNSKEY;
                break;
            case RRType.DS:
                sql = DEL_DS;
                break;
            case RRType.RRSIG:
                sql = DEL_RRSIG;
                break;
            case RRType.NSEC:
                sql = DEL_NSEC;
                break;
            case RRType.NSEC3:
                sql = DEL_NSEC3;
                break;
            case RRType.NSEC3PARAM:
                sql = DEL_NSEC3PARAM;
                break;
            default:
                sql = DEL_UNKNOWN;
                break;
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            if (typeIsUnknown(type)) {
                stmt.setInt(2, type);
            }
            stmt.executeUpdate();
        }
    }

    private boolean typeIsUnknown(int type) {
        return switch (type) {
            case RRType.A,
                    RRType.AAAA,
                    RRType.CNAME,
                    RRType.MX,
                    RRType.NS,
                    RRType.PTR,
                    RRType.SOA,
                    RRType.TXT,
                    RRType.DNSKEY,
                    RRType.DS,
                    RRType.RRSIG,
                    RRType.NSEC,
                    RRType.NSEC3,
                    RRType.NSEC3PARAM -> false;
            default -> true;
        };
    }
    /**
     * Clears the database tables of all entries
     *
     * @throws SQLException
     */
    public void clearAll() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (String table : ALL_RDATA_TABLES) {
                stmt.addBatch("TRUNCATE TABLE " + table);
            }
            stmt.executeBatch();
        }
    }

    /**
     * Checks the Database tables for expired entries and deletes them
     *
     * @throws SQLException
     */
    public void deleteExpired() throws SQLException {
        for (String table : ALL_RDATA_TABLES) {
            try (PreparedStatement stmt =
                    conn.prepareStatement("DELETE FROM " + table + " WHERE now() >= expires_at")) {
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
        Set<String> keys = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            for (ResourceRecord r : group) {
                String name = String.join(".", r.getName()).toLowerCase();
                if (r.getRdata() instanceof RDataUnknown unknown) {
                    String key = name + "|" + unknown.getType();
                    if (keys.add(key)) {
                        stmt.setString(1, name);
                        stmt.setInt(2, unknown.getType());
                        stmt.addBatch();
                    }
                } else {
                    if (keys.add(name)) {
                        stmt.setString(1, name);
                        stmt.addBatch();
                    }
                }
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
        if (group.isEmpty()) return;
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
     * Inserts a given array of ResourceRecords into the database using per-type batching.
     *
     * @param records
     * @throws SQLException
     */
    public void insertAll(ResourceRecord[] records) throws SQLException {
        Map<Integer, List<ResourceRecord>> byType = new HashMap<>();
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
            batchInsert(byType.getOrDefault(RRType.DS, List.of()), DEL_DS, SQL_DS, this::fillDS);
            batchInsert(byType.getOrDefault(RRType.DNSKEY, List.of()), DEL_DNSKEY, SQL_DNSKEY, this::fillDNSKEY);
            batchInsert(byType.getOrDefault(RRType.RRSIG, List.of()), DEL_RRSIG, SQL_RRSIG, this::fillRRSIG);
            batchInsert(byType.getOrDefault(RRType.NSEC, List.of()), DEL_NSEC, SQL_NSEC, this::fillNSEC);
            batchInsert(byType.getOrDefault(RRType.NSEC3, List.of()), DEL_NSEC3, SQL_NSEC3, this::fillNSEC3);
            batchInsert(
                    byType.getOrDefault(RRType.NSEC3PARAM, List.of()),
                    DEL_NSEC3PARAM,
                    SQL_NSEC3PARAM,
                    this::fillNSEC3PARAM);

            List<ResourceRecord> unknownRecords = new ArrayList<>();
            for (Map.Entry<Integer, List<ResourceRecord>> entry : byType.entrySet()) {
                if (typeIsUnknown(entry.getKey())) {
                    unknownRecords.addAll(entry.getValue());
                }
            }
            batchInsert(unknownRecords, DEL_UNKNOWN, SQL_UNKNOWN, this::fillUnknown);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close cache database connection: " + e.getMessage());
        }
    }
}
