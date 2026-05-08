package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS PTR (Pointer) record.
 * Used for reverse DNS lookups, mapping an IP address back to a domain name.
 */
public class ResourceRecordPTR extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
