package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS A record.
 * Maps a domain name to an IPv4 address (32-bit).
 */
public class ResourceRecordA extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}