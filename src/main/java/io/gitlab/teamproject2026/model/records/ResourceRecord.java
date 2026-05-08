package io.gitlab.teamproject2026.model.records;

/**
 * Interface for all DNS resource record types.
 * Each implementing class defines its own type-specific fields.
 */
public interface ResourceRecord {

    /**
     * Serializes this resource record into its binary DNS wire format.
     *
     * @return byte array representing this record in DNS wire format
     */
    byte[] to_bytes();

    /**
     * Deserializes a resource record from its binary DNS wire format.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new ResourceRecord instance populated with the parsed data
     */
    static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}