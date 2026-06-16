package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

import java.util.Arrays;
import java.util.NavigableSet;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

/**
 * Represents a DNS NS (Name Server) record.
 * Specifies the authoritative name server for a domain.
 */
public class RDataNS implements RData {
    private String[] nsdname;

    public RDataNS(){}
    public RDataNS(String[] nsdname){
        this.nsdname = nsdname;
    }

    @Override
    public RRType getType() {
        return RRType.NS;
    }

    public String[] getNsdname() { return nsdname; }
    public void setNsdname(String[] nsdname) { this.nsdname = nsdname; }

    @Override
    public String toString(){
        return String.join(".", nsdname) + ".";
    }

    @Override
    public byte[] toBytes() {
        return encodeName(nsdname);
    }

    public static RDataNS fromBytes(byte[] bytes, int byteIndex) {
        return new RDataNS(DNSParser.parseName(bytes, byteIndex));
    }
}