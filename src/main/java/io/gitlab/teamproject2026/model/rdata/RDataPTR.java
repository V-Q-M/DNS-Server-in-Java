package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;

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
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataPTR fromBytes(byte[] bytes) {
        RDataPTR rDataPTR = new RDataPTR();
        rDataPTR.setPtrdname(DNSParser.parseName(bytes, 0));
        return rDataPTR;
    }
}