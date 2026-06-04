package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

import java.util.Arrays;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;
/**
 * Represents a DNS CNAME (Canonical Name) record.
 * Aliases one domain name to another.
 */
public class RDataCNAME implements RData {
    private String[] cname;

    public RDataCNAME(){}
    public RDataCNAME(String[] cname){
        this.cname = cname;
    }

    @Override
    public RRType getType() {
        return RRType.CNAME;
    }

    public String[] getCname() { return cname; }
    public void setCname(String[] cname) { this.cname = cname; }

    @Override
    public String toString(){
        return String.join(".", cname) + ".";
    }

    @Override
    public byte[] toBytes() {
        return encodeName(cname);
    }

    public static RDataCNAME fromBytes(byte[] bytes, int byteIndex) {
        return new RDataCNAME(DNSParser.parseName(bytes, byteIndex));
    }
}