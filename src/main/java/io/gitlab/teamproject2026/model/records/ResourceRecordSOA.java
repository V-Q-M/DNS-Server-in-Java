package io.gitlab.teamproject2026.model.records;

/**
 * Represents a DNS SOA (Start of Authority) record.
 * Contains administrative information about a DNS zone.
 */
public class ResourceRecordSOA extends ResourceRecord {

    @Override
    public byte[] to_bytes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
