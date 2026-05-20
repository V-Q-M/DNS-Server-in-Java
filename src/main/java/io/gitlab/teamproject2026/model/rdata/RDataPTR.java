package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;

import java.util.Arrays;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

/**
 * Represents a DNS PTR (Pointer) record.
 * Used for reverse DNS lookups, mapping an IP address back to a domain name.
 */
public class RDataPTR implements RData {
    public static final int TYPE = 12;

    private String[] ptrdname;

    public String[] getPtrdname() { return ptrdname; }
    public void setPtrdname(String[] ptrdname) { this.ptrdname = ptrdname; }

    @Override
    public String toString(){
        return String.join(".", ptrdname) + ".";
    }

    @Override
    public byte[] toBytes() {
        return encodeName(String.join(".", ptrdname) + ".");
    }

    public static RDataPTR fromBytes(byte[] bytes, int byteIndex) {
        RDataPTR rDataPTR = new RDataPTR();
        rDataPTR.setPtrdname(DNSParser.parseName(bytes, byteIndex));
        return rDataPTR;
    }
}