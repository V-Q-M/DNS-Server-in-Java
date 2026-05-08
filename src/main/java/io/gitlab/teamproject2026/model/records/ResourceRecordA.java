package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS A record.
 * Maps a domain name to an IPv4 address (32-bit).
 */
public class ResourceRecordA implements ResourceRecord {
    public static final int TYPE = 1;
    
    private byte[] address;

    public byte[] getAddress() { return address; }
    public void setAddress(byte[] address) { this.address = address; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecordA from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}