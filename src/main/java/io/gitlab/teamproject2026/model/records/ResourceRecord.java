package io.gitlab.teamproject2026.model.records;

/**
 * Abstract base class for all DNS resource record types.
 * Subclasses represent specific RR types (A, AAAA, CNAME, NS, MX, SOA, PTR, TXT).
 */
public abstract class ResourceRecord {
    protected String rdata;

    public String getRdata() { return rdata; }
    public void setRdata(String rdata) { this.rdata = rdata; }

    /**
     * Serializes this resource record into its binary DNS wire format.
     * The resulting byte array can be used directly in a DNS response packet.
     *
     * @return byte array representing this record in DNS wire format
     */
    public abstract byte[] to_bytes();

    /**
     * Deserializes a resource record from its binary DNS wire format.
     * Parses the given byte array and returns a populated instance of this record type.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @return a new ResourceRecord instance populated with the parsed data
     */
    public static ResourceRecord from_bytes(byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
