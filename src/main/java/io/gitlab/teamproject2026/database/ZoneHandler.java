package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.model.rdata.dnssec.*;
import java.sql.*;
import java.util.*;

/**
 * Provides persistence operations for authoritative DNS zone data. Handles insertion, deletion, replacement, and lookup
 * of resource records stored in the database.
 */
public class ZoneHandler {
    private final Connection conn;

    private static final String SQL_A =
            "INSERT INTO zone_a_rdata    (zone, name, rrtype, rrclass, ttl, rdlength, ipv4) VALUES (?, ?, 'A',    'IN', ?, ?, ?)";
    private static final String SQL_AAAA =
            "INSERT INTO zone_aaaa_rdata  (zone, name, rrtype, rrclass, ttl, rdlength, ipv6) VALUES (?, ?, 'AAAA', 'IN', ?, ?, ?)";
    private static final String SQL_CNAME =
            "INSERT INTO zone_cname_rdata (zone, name, rrtype, rrclass, ttl, rdlength, cname) VALUES (?, ?, 'CNAME','IN', ?, ?, ?)";
    private static final String SQL_MX =
            "INSERT INTO zone_mx_rdata    (zone, name, rrtype, rrclass, ttl, rdlength, pref, exchange) VALUES (?, ?, 'MX',   'IN', ?, ?, ?, ?)";
    private static final String SQL_NS =
            "INSERT INTO zone_ns_rdata    (zone, name, rrtype, rrclass, ttl, rdlength, nsdname) VALUES (?, ?, 'NS',   'IN', ?, ?, ?)";
    private static final String SQL_PTR =
            "INSERT INTO zone_ptr_rdata   (zone, name, rrtype, rrclass, ttl, rdlength, ptrdname) VALUES (?, ?, 'PTR',  'IN', ?, ?, ?)";
    private static final String SQL_SOA =
            "INSERT INTO zone_soa_rdata   (zone, name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum) VALUES (?, ?, 'SOA',  'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_TXT =
            "INSERT INTO zone_txt_rdata   (zone, name, rrtype, rrclass, ttl, rdlength, txtdata) VALUES (?, ?, 'TXT',  'IN', ?, ?, ?)";

    // DNSSEC
    private static final String SQL_DS =
            "INSERT INTO zone_ds_rdata (zone, name, rrtype, rrclass, ttl, rdlength, key_tag, algorithm, digest_type, digest) VALUES (?, ?, 'DS', 'IN', ?, ?, ?, ?, ?, ?)";
    private static final String SQL_DNSKEY =
            "INSERT INTO zone_dnskey_rdata (zone, name, rrtype, rrclass, ttl, rdlength, flags, protocol, algorithm, publickey) VALUES (?, ?, 'DNSKEY', 'IN', ?, ?, ?, ?, ?, ?)";
    private static final String SQL_RRSIG =
            "INSERT INTO zone_rrsig_rdata (zone, name, rrtype, rrclass, ttl, rdlength, type_covered, algorithm, labels, original_ttl, expiration, inception, key_tag, signer_name, signature) VALUES (?, ?, 'RRSIG', 'IN', ?, ?, ?::RRTypeEnum, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_NSEC =
            "INSERT INTO zone_nsec_rdata (zone, name, rrtype, rrclass, ttl, rdlength, next_domain_name, type_bit_maps) VALUES (?, ?, 'NSEC', 'IN', ?, ?, ?, ?)";
    private static final String SQL_NSEC3 =
            "INSERT INTO zone_nsec3_rdata (zone, name, rrtype, rrclass, ttl, rdlength, hash_algorithm, flags, iterations, salt, next_hashed_owner_name, type_bit_maps) VALUES (?, ?, 'NSEC3', 'IN', ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_NSEC3PARAM =
            "INSERT INTO zone_nsec3param_rdata (zone, name, rrtype, rrclass, ttl, rdlength, hash_algorithm, flags, iterations, salt) VALUES (?, ?, 'NSEC3PARAM', 'IN', ?, ?, ?, ?, ?, ?)";

    private static final String DEL_A = "DELETE FROM zone_a_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_AAAA = "DELETE FROM zone_aaaa_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_CNAME = "DELETE FROM zone_cname_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_MX = "DELETE FROM zone_mx_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_NS = "DELETE FROM zone_ns_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_PTR = "DELETE FROM zone_ptr_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_SOA = "DELETE FROM zone_soa_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_TXT = "DELETE FROM zone_txt_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";

    // DNSSEC
    private static final String DEL_DNSKEY = "DELETE FROM zone_dnskey_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_DS = "DELETE FROM zone_ds_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_RRSIG = "DELETE FROM zone_rrsig_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_NSEC = "DELETE FROM zone_nsec_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_NSEC3 = "DELETE FROM zone_nsec3_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";
    private static final String DEL_NSEC3PARAM =
            "DELETE FROM zone_nsec3param_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?";

    private static final String[] ALL_RDATA_TABLES = {
        "zone_a_rdata",
        "zone_aaaa_rdata",
        "zone_cname_rdata",
        "zone_mx_rdata",
        "zone_ns_rdata",
        "zone_ptr_rdata",
        "zone_soa_rdata",
        "zone_txt_rdata",
        "zone_dnskey_rdata",
        "zone_ds_rdata",
        "zone_rrsig_rdata",
        "zone_nsec_rdata",
        "zone_nsec3_rdata",
        "zone_nsec3param_rdata"
    };

    /** Maps a database row to a concrete RData implementation. */
    @FunctionalInterface
    private interface RDataRowMapper {
        RData map(ResultSet rs) throws SQLException;
    }

    /** Fills a prepared statement with record-specific parameters. */
    @FunctionalInterface
    private interface StatementFiller {
        void fill(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException;
    }

    /**
     * Creates a handler using the shared database connection.
     *
     * @param provider database connection provider
     */
    public ZoneHandler(DatabaseConnectionProvider provider) {
        this.conn = provider.getConnection();
    }

    /**
     * Executes a type-specific lookup query and reconstructs the resulting resource records using the supplied mapper.
     *
     * @param name queried owner name
     * @param sql SQL statement used for the lookup
     * @param type expected RR type
     * @param zone authoritative zone
     * @param mapper converts ResultSet rows into RData objects
     * @return matching resource records
     * @throws SQLException if the database query fails
     */
    private List<ResourceRecord> lookupType(String name, String sql, int type, String zone, RDataRowMapper mapper)
            throws SQLException {
        List<ResourceRecord> records = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, zone);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceRecord rr = new ResourceRecord();
                    rr.setName(rs.getString("name").split("\\."));
                    rr.setRrclass(RRClass.IN);
                    rr.setTtl(rs.getLong("ttl"));
                    rr.setRdata(mapper.map(rs));
                    records.add(rr);
                }
            }
        }
        return records;
    }

    /**
     * Returns the SOA record associated with the specified zone.
     *
     * @param zone authoritative zone name
     * @return the zone's SOA RDATA
     * @throws SQLException if the database query fails
     * @throws NoSuchElementException if the zone does not contain an SOA record
     */
    public RDataSOA lookupZoneSoa(String zone) throws SQLException {
        return (RDataSOA) lookup(zone, RRType.SOA, zone).getFirst().getRdata();
    }

    /**
     * Atomically replaces all records belonging to a zone.
     *
     * @param zone zone to replace
     * @throws SQLException if the database update fails
     */
    public void replaceZone(DNSZone zone) throws SQLException {
        clearZone(zone.origin());
        insertZone(zone);
    }

    /**
     * Removes all records belonging to the specified zone.
     *
     * @param zone zone to clear
     * @throws SQLException if the database update fails
     */
    public void clearZone(String zone) throws SQLException {
        Statement stmt = conn.createStatement();
        for (String table : ALL_RDATA_TABLES) {
            stmt.addBatch("DELETE FROM " + table + " WHERE zone = '" + zone + "'");
        }
        stmt.executeBatch();
    }

    /**
     * Returns the full ResourceRecord from the Database, given a name and type Looks for entries that end in name
     *
     * @param name of the RREntry (check = LIKE %.{name})
     * @param type of the RREntry
     * @param zone the RREntry is under
     * @return RR Array of all matching RRs
     * @throws SQLException
     */
    public List<ResourceRecord> lookup(String name, int type, String zone) throws SQLException {
        name = name.toLowerCase();
        switch (type) {
            case RRType.A:
                return lookupType(
                        name,
                        "SELECT * FROM zone_a_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.A,
                        zone,
                        rs -> {
                            RDataA rdata = new RDataA();
                            rdata.setAddress(rs.getBytes("ipv4"));
                            return rdata;
                        });
            case RRType.AAAA:
                return lookupType(
                        name,
                        "SELECT * FROM zone_aaaa_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.AAAA,
                        zone,
                        rs -> {
                            RDataAAAA rdata = new RDataAAAA();
                            rdata.setAddress(rs.getBytes("ipv6"));
                            return rdata;
                        });
            case RRType.CNAME:
                return lookupType(
                        name,
                        "SELECT * FROM zone_cname_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.CNAME,
                        zone,
                        rs -> {
                            RDataCNAME rdata = new RDataCNAME();
                            rdata.setCname(rs.getString("cname").split("\\."));
                            return rdata;
                        });
            case RRType.MX:
                return lookupType(
                        name,
                        "SELECT * FROM zone_mx_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.MX,
                        zone,
                        rs -> {
                            RDataMX rdata = new RDataMX();
                            rdata.setPreference(rs.getInt("pref"));
                            rdata.setExchange(rs.getString("exchange").split("\\."));
                            return rdata;
                        });
            case RRType.NS:
                return lookupType(
                        name,
                        "SELECT * FROM zone_ns_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.NS,
                        zone,
                        rs -> {
                            RDataNS rdata = new RDataNS();
                            rdata.setNsdname(rs.getString("nsdname").split("\\."));
                            return rdata;
                        });
            case RRType.PTR:
                return lookupType(
                        name,
                        "SELECT * FROM zone_ptr_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.PTR,
                        zone,
                        rs -> {
                            RDataPTR rdata = new RDataPTR();
                            rdata.setPtrdname(rs.getString("ptrdname").split("\\."));
                            return rdata;
                        });
            case RRType.SOA:
                return lookupType(
                        name,
                        "SELECT * FROM zone_soa_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.SOA,
                        zone,
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
                        "SELECT * FROM zone_txt_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.TXT,
                        zone,
                        rs -> {
                            RDataTXT rdata = new RDataTXT();
                            rdata.setTxtdata((String[]) rs.getArray("txtdata").getArray());
                            return rdata;
                        });
            case RRType.DS:
                return lookupType(
                        name,
                        "SELECT * FROM zone_ds_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.DS,
                        zone,
                        rs -> new RDataDS(
                                rs.getInt("key_tag"),
                                rs.getInt("algorithm"),
                                rs.getInt("digest_type"),
                                rs.getBytes("digest")));
            case RRType.DNSKEY:
                return lookupType(
                        name,
                        "SELECT * FROM zone_dnskey_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.DNSKEY,
                        zone,
                        rs -> new RDataDNSKEY(
                                rs.getInt("flags"),
                                rs.getInt("protocol"),
                                rs.getInt("algorithm"),
                                rs.getBytes("publickey")));
            case RRType.RRSIG:
                return lookupType(
                        name,
                        "SELECT * FROM zone_rrsig_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.RRSIG,
                        zone,
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
                        "SELECT * FROM zone_nsec_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.NSEC,
                        zone,
                        rs -> new RDataNSEC(
                                rs.getString("next_domain_name").split("\\."), rs.getBytes("type_bit_maps")));
            case RRType.NSEC3:
                return lookupType(
                        name,
                        "SELECT * FROM zone_nsec3_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.NSEC3,
                        zone,
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
                        "SELECT * FROM zone_nsec3param_rdata WHERE LOWER(name) = ? AND LOWER(zone) = ?",
                        RRType.NSEC3PARAM,
                        zone,
                        rs -> new RDataNSEC3PARAM(
                                rs.getInt("hash_algorithm"),
                                rs.getInt("flags"),
                                rs.getInt("iterations"),
                                rs.getBytes("salt")));
            default:
                return new ArrayList<>();
        }
    }

    /*
     * Helper functions to fill in Record statements
     */
    private void fillCommon(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        stmt.setString(1, zone);
        stmt.setString(2, String.join(".", r.getName()));
        stmt.setLong(3, r.getTtl());
        stmt.setInt(4, r.getRdlength());
    }

    /**
     * Executes a type-specific INSERT for a single resource record.
     *
     * @param stmt INSERT statement to execute
     * @param r record to insert
     * @param zone target zone
     * @throws SQLException if insertion fails
     */
    private void fillA(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setBytes(5, ((RDataA) r.getRdata()).getAddress());
    }

    private void fillAAAA(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setBytes(5, ((RDataAAAA) r.getRdata()).getAddress());
    }

    private void fillCNAME(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setString(5, String.join(".", ((RDataCNAME) r.getRdata()).getCname()));
    }

    private void fillMX(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataMX rdata = (RDataMX) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getPreference());
        stmt.setString(6, String.join(".", rdata.getExchange()));
    }

    private void fillNS(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setString(5, String.join(".", ((RDataNS) r.getRdata()).getNsdname()));
    }

    private void fillPTR(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setString(5, String.join(".", ((RDataPTR) r.getRdata()).getPtrdname()));
    }

    private void fillSOA(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataSOA rdata = (RDataSOA) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setString(5, String.join(".", rdata.getMname()));
        stmt.setString(6, String.join(".", rdata.getRname()));
        stmt.setLong(7, rdata.getSerial());
        stmt.setLong(8, rdata.getRefresh());
        stmt.setLong(9, rdata.getRetry());
        stmt.setLong(10, rdata.getExpire());
        stmt.setLong(11, rdata.getMinimum());
    }

    private void fillTXT(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        fillCommon(stmt, r, zone);
        stmt.setArray(5, conn.createArrayOf("text", ((RDataTXT) r.getRdata()).getTxtdata()));
    }

    private void fillDS(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataDS rdata = (RDataDS) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getKeyTag());
        stmt.setInt(6, rdata.getAlgorithm());
        stmt.setInt(7, rdata.getDigestType());
        stmt.setBytes(8, rdata.getDigest());
    }

    private void fillDNSKEY(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataDNSKEY rdata = (RDataDNSKEY) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getFlags());
        stmt.setInt(6, rdata.getProtocol());
        stmt.setInt(7, rdata.getAlgorithm());
        stmt.setBytes(8, rdata.getPublickey());
    }

    private void fillRRSIG(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataRRSIG rdata = (RDataRRSIG) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getTypeCovered());
        stmt.setInt(6, rdata.getAlgorithm());
        stmt.setInt(7, rdata.getLabels());
        stmt.setLong(8, rdata.getOriginalTtl());
        stmt.setLong(9, rdata.getSignatureExpiration());
        stmt.setLong(10, rdata.getSignatureInception());
        stmt.setInt(11, rdata.getKeyTag());
        stmt.setString(12, String.join(".", rdata.getSignerName()));
        stmt.setBytes(13, rdata.getSignature());
    }

    private void fillNSEC(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataNSEC rdata = (RDataNSEC) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setString(5, String.join(".", rdata.getNextDomainName()));
        stmt.setBytes(6, rdata.getTypeBitMaps());
    }

    private void fillNSEC3(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataNSEC3 rdata = (RDataNSEC3) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getHashAlgorithm());
        stmt.setInt(6, rdata.getFlags());
        stmt.setInt(7, rdata.getIterations());
        stmt.setBytes(8, rdata.getSalt());
        stmt.setBytes(9, rdata.getNextHashedOwnerName());
        stmt.setBytes(10, rdata.getTypeBitMaps());
    }

    private void fillNSEC3PARAM(PreparedStatement stmt, ResourceRecord r, String zone) throws SQLException {
        RDataNSEC3PARAM rdata = (RDataNSEC3PARAM) r.getRdata();
        fillCommon(stmt, r, zone);
        stmt.setInt(5, rdata.getHashAlgorithm());
        stmt.setInt(6, rdata.getFlags());
        stmt.setInt(7, rdata.getIterations());
        stmt.setBytes(8, rdata.getSalt());
    }

    private void singleInsert(String insertSql, ResourceRecord r, String zone, StatementFiller filler)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            filler.fill(stmt, r, zone);
            stmt.executeUpdate();
        }
    }

    /**
     * Inserts a given ResourceRecord into the Database
     *
     * @param record
     * @param zone
     * @throws SQLException
     */
    void insert(ResourceRecord record, String zone) throws SQLException {
        delete(String.join(".", record.getName()).toLowerCase(), record.getType(), zone);
        switch (record.getType()) {
            case RRType.A:
                singleInsert(SQL_A, record, zone, this::fillA);
                break;
            case RRType.AAAA:
                singleInsert(SQL_AAAA, record, zone, this::fillAAAA);
                break;
            case RRType.CNAME:
                singleInsert(SQL_CNAME, record, zone, this::fillCNAME);
                break;
            case RRType.MX:
                singleInsert(SQL_MX, record, zone, this::fillMX);
                break;
            case RRType.NS:
                singleInsert(SQL_NS, record, zone, this::fillNS);
                break;
            case RRType.PTR:
                singleInsert(SQL_PTR, record, zone, this::fillPTR);
                break;
            case RRType.SOA:
                singleInsert(SQL_SOA, record, zone, this::fillSOA);
                break;
            case RRType.TXT:
                singleInsert(SQL_TXT, record, zone, this::fillTXT);
                break;
            case RRType.DS:
                singleInsert(SQL_DS, record, zone, this::fillDS);
                break;
            case RRType.DNSKEY:
                singleInsert(SQL_DNSKEY, record, zone, this::fillDNSKEY);
                break;
            case RRType.RRSIG:
                singleInsert(SQL_RRSIG, record, zone, this::fillRRSIG);
                break;
            case RRType.NSEC:
                singleInsert(SQL_NSEC, record, zone, this::fillNSEC);
                break;
            case RRType.NSEC3:
                singleInsert(SQL_NSEC3, record, zone, this::fillNSEC3);
                break;
            case RRType.NSEC3PARAM:
                singleInsert(SQL_NSEC3PARAM, record, zone, this::fillNSEC3PARAM);
                break;
        }
    }

    /**
     * Deletes a ResourceRecord of given name, type from a zone
     *
     * @param name
     * @param type
     * @param zone
     * @throws SQLException
     */
    public void delete(String name, int type, String zone) throws SQLException {
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
                return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, zone);
            stmt.executeUpdate();
        }
    }

    /**
     * Clears the database tables of all entries
     *
     * @throws SQLException
     */
    public void clearAll() throws SQLException {
        Statement stmt = conn.createStatement();
        for (String table : ALL_RDATA_TABLES) {
            stmt.addBatch("TRUNCATE TABLE " + table);
        }
        stmt.executeBatch();
    }

    /**
     * batch-inserts RRs.
     *
     * @param group the records to insert
     * @param zone the zon to insert to
     * @param insertSql the type-specific INSERT SQL
     * @param filler fills the INSERT statement parameters for a given record
     * @throws SQLException
     */
    private void batchInsert(List<ResourceRecord> group, String zone, String insertSql, StatementFiller filler)
            throws SQLException {
        if (group.isEmpty()) return;
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (ResourceRecord r : group) {
                filler.fill(stmt, r, zone);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Inserts a given array of ResourceRecords into the database using per-type batching.
     *
     * @param dnsZone the zone to insert
     * @throws SQLException
     */
    public void insertZone(DNSZone dnsZone) throws SQLException {
        ResourceRecord[] records = dnsZone.getRecords().toArray(new ResourceRecord[0]);
        String zone = dnsZone.origin();
        Map<Integer, List<ResourceRecord>> byType = new HashMap<>();
        for (ResourceRecord record : records) {
            byType.computeIfAbsent(record.getType(), k -> new ArrayList<>()).add(record);
        }
        conn.setAutoCommit(false);
        try {
            batchInsert(byType.getOrDefault(RRType.A, List.of()), zone, SQL_A, this::fillA);
            batchInsert(byType.getOrDefault(RRType.AAAA, List.of()), zone, SQL_AAAA, this::fillAAAA);
            batchInsert(byType.getOrDefault(RRType.CNAME, List.of()), zone, SQL_CNAME, this::fillCNAME);
            batchInsert(byType.getOrDefault(RRType.MX, List.of()), zone, SQL_MX, this::fillMX);
            batchInsert(byType.getOrDefault(RRType.NS, List.of()), zone, SQL_NS, this::fillNS);
            batchInsert(byType.getOrDefault(RRType.PTR, List.of()), zone, SQL_PTR, this::fillPTR);
            batchInsert(byType.getOrDefault(RRType.SOA, List.of()), zone, SQL_SOA, this::fillSOA);
            batchInsert(byType.getOrDefault(RRType.TXT, List.of()), zone, SQL_TXT, this::fillTXT);

            batchInsert(byType.getOrDefault(RRType.DS, List.of()), zone, SQL_DS, this::fillDS);
            batchInsert(byType.getOrDefault(RRType.DNSKEY, List.of()), zone, SQL_DNSKEY, this::fillDNSKEY);
            batchInsert(byType.getOrDefault(RRType.RRSIG, List.of()), zone, SQL_RRSIG, this::fillRRSIG);
            batchInsert(byType.getOrDefault(RRType.NSEC, List.of()), zone, SQL_NSEC, this::fillNSEC);
            batchInsert(byType.getOrDefault(RRType.NSEC3, List.of()), zone, SQL_NSEC3, this::fillNSEC3);
            batchInsert(byType.getOrDefault(RRType.NSEC3PARAM, List.of()), zone, SQL_NSEC3PARAM, this::fillNSEC3PARAM);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
