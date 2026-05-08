package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS CNAME (Canonical Name) record.
 * Aliases one domain name to another.
 */
public class ResourceRecordCNAME extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}