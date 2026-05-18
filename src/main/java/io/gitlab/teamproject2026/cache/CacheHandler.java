package io.gitlab.teamproject2026.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.*;

public class CacheHandler {
    private final Connection conn;

    public CacheHandler(CacheConnectionProvider provider) throws SQLException, ClassNotFoundException {
        this.conn = provider.getConnection();
    }

    /**
     * Returns the full ResourceRecord from the Database, given a name and type
     * @param name
     * @param type
     * @return
     * @throws SQLException
     */
    public ResourceRecord[] lookup(String name, RRType type) throws SQLException {
        name = name.toLowerCase();
        switch (type) {
            case A: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM a_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.A);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataA rdata = new RDataA();
                            rdata.setAddress(rs.getBytes("ipv4"));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case AAAA: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM aaaa_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.AAAA);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataAAAA rdata = new RDataAAAA();
                            rdata.setAddress(rs.getBytes("ipv6"));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case CNAME: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM cname_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.CNAME);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataCNAME rdata = new RDataCNAME();
                            rdata.setCname(rs.getString("cname").split("\\."));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case MX: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM mx_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.MX);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataMX rdata = new RDataMX();
                            rdata.setPreference(rs.getInt("pref"));
                            rdata.setExchange(rs.getString("exchange").split("\\."));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case NS: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ns_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.NS);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataNS rdata = new RDataNS();
                            rdata.setNsdname(rs.getString("nsdname").split("\\."));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case PTR: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ptr_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.PTR);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataPTR rdata = new RDataPTR();
                            rdata.setPtrdname(rs.getString("ptrdname").split("\\."));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case SOA: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM soa_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.SOA);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataSOA rdata = new RDataSOA();
                            rdata.setMname(rs.getString("mname").split("\\."));
                            rdata.setRname(rs.getString("rname").split("\\."));
                            rdata.setSerial(rs.getLong("serial"));
                            rdata.setRefresh(rs.getLong("refresh"));
                            rdata.setRetry(rs.getLong("retry"));
                            rdata.setExpire(rs.getLong("expire"));
                            rdata.setMinimum(rs.getLong("minimum"));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            case TXT: {
                List<ResourceRecord> records = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM txt_rdata WHERE name = ?")) {
                    stmt.setString(1, name);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ResourceRecord rr = new ResourceRecord();
                            rr.setName(name.split("\\."));
                            rr.setType(RRType.TXT);
                            rr.setRrclass(RRClass.IN);
                            rr.setTtl(rs.getLong("ttl"));
                            RDataTXT rdata = new RDataTXT();
                            rdata.setTxtdata(rs.getString("txtdata"));
                            rr.setRdata(rdata);
                            records.add(rr);
                        }
                    }
                }
                return records.toArray(new ResourceRecord[0]);
            }
            default:
                return new ResourceRecord[0];
        }
    }

    /**
     * Inserts a given ResourceRecord into the Database
     * @param record
     * @throws SQLException
     */
    public void insert(ResourceRecord record) throws SQLException {
        String name = String.join(".", record.getName()).toLowerCase();
        switch (record.getType()) {
            case A: {
                RDataA rdata = (RDataA) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO a_rdata (name, rrtype, rrclass, ttl, rdlength, ipv4) VALUES (?, 'A', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setBytes(4, rdata.getAddress());
                    stmt.executeUpdate();
                }
                break;
            }
            case AAAA: {
                RDataAAAA rdata = (RDataAAAA) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO aaaa_rdata (name, rrtype, rrclass, ttl, rdlength, ipv6) VALUES (?, 'AAAA', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setBytes(4, rdata.getAddress());
                    stmt.executeUpdate();
                }
                break;
            }
            case CNAME: {
                RDataCNAME rdata = (RDataCNAME) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname) VALUES (?, 'CNAME', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setString(4, String.join(".", rdata.getCname()));
                    stmt.executeUpdate();
                }
                break;
            }
            case MX: {
                RDataMX rdata = (RDataMX) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO mx_rdata (name, rrtype, rrclass, ttl, rdlength, pref, exchange) VALUES (?, 'MX', 'IN', ?, ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setInt(4, rdata.getPreference());
                    stmt.setString(5, String.join(".", rdata.getExchange()));
                    stmt.executeUpdate();
                }
                break;
            }
            case NS: {
                RDataNS rdata = (RDataNS) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO ns_rdata (name, rrtype, rrclass, ttl, rdlength, nsdname) VALUES (?, 'NS', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setString(4, String.join(".", rdata.getNsdname()));
                    stmt.executeUpdate();
                }
                break;
            }
            case PTR: {
                RDataPTR rdata = (RDataPTR) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO ptr_rdata (name, rrtype, rrclass, ttl, rdlength, ptrdname) VALUES (?, 'PTR', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setString(4, String.join(".", rdata.getPtrdname()));
                    stmt.executeUpdate();
                }
                break;
            }
            case SOA:{
                RDataSOA rdata = (RDataSOA) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO soa_rdata (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum) VALUES (?, 'SOA', 'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setString(4, String.join(".", rdata.getMname()));
                    stmt.setString(5, String.join(".", rdata.getRname()));
                    stmt.setLong(6, rdata.getSerial());
                    stmt.setLong(7, rdata.getRefresh());
                    stmt.setLong(8, rdata.getRetry());
                    stmt.setLong(9, rdata.getExpire());
                    stmt.setLong(10, rdata.getMinimum());
                    stmt.executeUpdate();
                }
                break;
            }
            case TXT:{
                RDataTXT rdata = (RDataTXT) record.getRdata();
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO txt_rdata (name, rrtype, rrclass, ttl, rdlength, txtdata) VALUES (?, 'TXT', 'IN', ?, ?, ?)")) {
                    stmt.setString(1, name);
                    stmt.setLong(2, record.getTtl());
                    stmt.setInt(3, record.getRdlength());
                    stmt.setString(4, rdata.getTxtdata());
                    stmt.executeUpdate();
                }
                break;
            }         
            default:
                break;
        }
    }

    /**
     * Deletes a ResourceRecord of given name and type
     * @param name
     * @param type
     * @throws SQLException
     */
    public void delete(String name, RRType type) throws SQLException {
        name = name.toLowerCase();
        String table;
        switch (type) {
            case A:     table = "a_rdata";    break;
            case AAAA:  table = "aaaa_rdata"; break;
            case CNAME: table = "cname_rdata"; break;
            case MX:    table = "mx_rdata";   break;
            case NS:    table = "ns_rdata";   break;
            case PTR:   table = "ptr_rdata";  break;
            case SOA:   table = "soa_rdata";  break;
            case TXT:   table = "txt_rdata";  break;
            default: return;
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE name = ?")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    /**
     * Clears the database tables of all entries
     * @throws SQLException
     */
    public void clearAll() throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE a_rdata, aaaa_rdata, cname_rdata, mx_rdata, ns_rdata, ptr_rdata, soa_rdata, txt_rdata");
        }
    }


    // TODO add more performant version to pack RRs of same type together and send to DB
    /**
    * Inserts a given array of ResourceRecords into the database 
    * @param records
    * @throws SQLException
    */
    public void insertAll(ResourceRecord[] records) throws SQLException {
        conn.setAutoCommit(false);
        try {
            for (ResourceRecord record : records) {
                insert(record);
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
