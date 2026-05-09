package io.gitlab.teamproject2026.model.rdata;

/**
 * Represents a DNS CNAME (Canonical Name) record.
 * Aliases one domain name to another.
 */
public class RDataCNAME implements RData {
    public static final int TYPE = 5;
    
    private String cname;

    public String getCname() { return cname; }
    public void setCname(String cname) { this.cname = cname; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataCNAME from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}