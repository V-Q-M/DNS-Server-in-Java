package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS NS (Name Server) record.
 * Specifies the authoritative name server for a domain.
 */
public class ResourceRecordNS extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
