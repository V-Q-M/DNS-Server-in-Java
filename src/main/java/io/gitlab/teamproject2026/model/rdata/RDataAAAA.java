package io.gitlab.teamproject2026.model.rdata;

/**
 * Represents a DNS AAAA record.
 * Maps a domain name to an IPv6 address (128-bit).
 */
public class RDataAAAA implements RData {
    public static final int TYPE = 28;

    private byte[] address;

    public byte[] getAddress() { return address; }
    public void setAddress(byte[] address) { this.address = address; }
    
    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static RDataAAAA fromBytes(byte[] bytes) {
        RDataAAAA rDataAAAA = new RDataAAAA();
        rDataAAAA.setAddress(bytes);
        return rDataAAAA;
    }
}