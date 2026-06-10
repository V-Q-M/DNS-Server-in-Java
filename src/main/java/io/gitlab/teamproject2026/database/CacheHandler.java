package io.gitlab.teamproject2026.database;

import java.sql.SQLException;

public class CacheHandler extends DatabaseHandler{
    public CacheHandler(DatabaseConnectionProvider provider){
        super(provider);
        SQL_A = "INSERT INTO a_rdata    (name, rrtype, rrclass, ttl, rdlength, ipv4,     expires_at) VALUES (?, 'A',    'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
        SQL_AAAA = "INSERT INTO aaaa_rdata  (name, rrtype, rrclass, ttl, rdlength, ipv6,     expires_at) VALUES (?, 'AAAA', 'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
        SQL_CNAME = "INSERT INTO cname_rdata (name, rrtype, rrclass, ttl, rdlength, cname,    expires_at) VALUES (?, 'CNAME','IN', ?, ?, ?,          now() + (? * interval '1 second'))";
        SQL_MX = "INSERT INTO mx_rdata    (name, rrtype, rrclass, ttl, rdlength, pref, exchange, expires_at) VALUES (?, 'MX',   'IN', ?, ?, ?, ?,       now() + (? * interval '1 second'))";
        SQL_NS = "INSERT INTO ns_rdata    (name, rrtype, rrclass, ttl, rdlength, nsdname,  expires_at) VALUES (?, 'NS',   'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
        SQL_PTR = "INSERT INTO ptr_rdata   (name, rrtype, rrclass, ttl, rdlength, ptrdname, expires_at) VALUES (?, 'PTR',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";
        SQL_SOA = "INSERT INTO soa_rdata   (name, rrtype, rrclass, ttl, rdlength, mname, rname, serial, refresh, retry, expire, minimum, expires_at) VALUES (?, 'SOA',  'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))";
        SQL_TXT = "INSERT INTO txt_rdata   (name, rrtype, rrclass, ttl, rdlength, txtdata,  expires_at) VALUES (?, 'TXT',  'IN', ?, ?, ?,          now() + (? * interval '1 second'))";

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
