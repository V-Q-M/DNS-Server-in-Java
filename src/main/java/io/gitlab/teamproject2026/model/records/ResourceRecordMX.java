package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS MX (Mail Exchange) record.
 * Specifies the mail server responsible for a domain.
 */
public class ResourceRecordMX extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}