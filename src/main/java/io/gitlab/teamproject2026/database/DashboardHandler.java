package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.logging.DNSEvent;

import java.sql.Connection;

public class DashboardHandler {
    private final Connection conn;

    private static final String SQL_INSERT =
            "INSERT INTO a_rdata    (name, rrtype, rrclass, ttl, rdlength, ipv4,     expires_at) VALUES (?, 'A',    'IN', ?, ?, ?,          now() + (? * interval '1 second'))";


    public DashboardHandler(DatabaseConnectionProvider provider) {
        this.conn = provider.getConnection();
    }

    public synchronized void storeDNSEvent(DNSEvent dnsEvent){

    }

}
