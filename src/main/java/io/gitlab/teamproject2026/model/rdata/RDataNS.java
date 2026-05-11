package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

/**
 * Represents a DNS NS (Name Server) record.
 * Specifies the authoritative name server for a domain.
 */
public class RDataNS implements RData {
    public static final int TYPE = 2;

    private String[] nsdname;

    public String[] getNsdname() { return nsdname; }
    public void setNsdname(String[] nsdname) { this.nsdname = nsdname; }

    @Override
    public byte[] toBytes() {
        return encodeName(String.join(".", nsdname));
    }

    public static RDataNS fromBytes(byte[] bytes) {
        RDataNS rDataNS = new RDataNS();
        rDataNS.setNsdname(DNSParser.parseName(bytes, 0));
        return  rDataNS;
    }
}