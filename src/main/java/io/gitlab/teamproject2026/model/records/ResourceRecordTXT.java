package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS TXT record.
 * Stores arbitrary text data associated with a domain.
 */
public class ResourceRecordTXT extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
