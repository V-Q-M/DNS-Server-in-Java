package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS NS (Name Server) record.
 * Specifies the authoritative name server for a domain.
 */
public class ResourceRecordNS implements ResourceRecord {
    public static final int TYPE = 2;
    
    private String nsdname;

    public String getNsdname() { return nsdname; }
    public void setNsdname(String nsdname) { this.nsdname = nsdname; }

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecordNS from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}