package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS AAAA record.
 * Maps a domain name to an IPv6 address (128-bit).
 */
public class ResourceRecordAAAA extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
