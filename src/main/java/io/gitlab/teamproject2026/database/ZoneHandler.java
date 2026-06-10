package io.gitlab.teamproject2026.database;

import java.sql.SQLException;

public class ZoneHandler extends DatabaseHandler{
    public ZoneHandler(DatabaseConnectionProvider provider){
        super(provider);
        SQL_A = "INSERT INTO a_rdata    (name, rrtype, rrclass, ttl, rdlength, ipv4, zone) VALUES (?, 'A',    'IN', ?, ?, ?, ?)";
        SQL_AAAA = "INSERT INTO aaaa_rdata  (name, rrtype, rrclass, ttl, rdlength, ipv6, zone) VALUES (?, 'AAAA', 'IN', ?, ?, ?, ?)";
        SQL_CNAME = "INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname, zone) VALUES (?, 'CNAME','IN', ?, ?, ?, ?)";
        SQL_MX = "INSERT INTO mx_rdata    (name, rrtype, rrclass, ttl, rdlength, pref, exchange, zone) VALUES (?, 'MX',   'IN', ?, ?, ?, ?, ?)";
        SQL_NS = "INSERT INTO ns_rdata    (name, rrtype, rrclass, ttl, rdlength, nsdname, zone) VALUES (?, 'NS',   'IN', ?, ?, ?, ?)";
        SQL_PTR = "INSERT INTO ptr_rdata   (name, rrtype, rrclass, ttl, rdlength, ptrdname, zone) VALUES (?, 'PTR',  'IN', ?, ?, ?, ?)";
        SQL_SOA = "INSERT INTO soa_rdata   (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum, zone) VALUES (?, 'SOA',  'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        SQL_TXT = "INSERT INTO txt_rdata   (name, rrtype, rrclass, ttl, rdlength, txtdata, zone) VALUES (?, 'TXT',  'IN', ?, ?, ?, ?)";

        DEL_A = "DELETE FROM a_rdata WHERE LOWER(name) = ?";
        DEL_AAAA = "DELETE FROM aaaa_rdata WHERE LOWER(name) = ?";
        DEL_CNAME = "DELETE FROM cname_rdata WHERE LOWER(name) = ?";
        DEL_MX = "DELETE FROM mx_rdata WHERE LOWER(name) = ?";
        DEL_NS = "DELETE FROM ns_rdata WHERE LOWER(name) = ?";
        DEL_PTR = "DELETE FROM ptr_rdata WHERE LOWER(name) = ?";
        DEL_SOA = "DELETE FROM soa_rdata WHERE LOWER(name) = ?";
        DEL_TXT = "DELETE FROM txt_rdata WHERE LOWER(name) = ?";
    }
}
