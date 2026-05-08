package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS PTR (Pointer) record.
 * Used for reverse DNS lookups, mapping an IP address back to a domain name.
 */
public class ResourceRecordPTR implements ResourceRecord {
    public static final int TYPE = 12;
    
    private String ptrdname;

    public String getPtrdname() { return ptrdname; }
    public void setPtrdname(String ptrdname) { this.ptrdname = ptrdname; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecordPTR from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}