package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;

import java.util.Arrays;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;
/**
 * Represents a DNS CNAME (Canonical Name) record.
 * Aliases one domain name to another.
 */
public class RDataCNAME implements RData {
    public static final int TYPE = 5;

    private String[] cname;

    public String[] getCname() { return cname; }
    public void setCname(String[] cname) { this.cname = cname; }

    @Override
    public String toString(){
        return String.join(".", cname) + ".";
    }

    @Override
    public byte[] toBytes() {
        String domain = String.join(".", cname);
        return encodeName(domain);
    }

    public static RDataCNAME fromBytes(byte[] bytes, int byteIndex) {
        RDataCNAME rDataCNAME = new RDataCNAME();
        rDataCNAME.setCname(DNSParser.parseName(bytes, byteIndex));
        return rDataCNAME;
    }
}