package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;

/**
 * Interface for all DNS RDATA types.
 * Each implementing class defines its own type-specific fields.
 */
public interface RData {

    /**
     * Serializes this resource record into its binary DNS wire format.
     *
     * @return byte array representing this record in DNS wire format
     */
    byte[] to_bytes();

    /**
     * Deserializes a rdata record from its binary DNS wire format.
     *
     * @param bytes raw bytes from an incoming DNS packet
     * @param byte_type the byte of the resource record from an incoming DNS packet
     * @return a new ResourceRecord instance populated with the parsed data
     */
    static RData from_bytes(byte[] bytes, byte byte_type) {
        RRType rrType = RRType.fromValue((int) byte_type);
        return switch (rrType) {
            case A -> RDataA.from_bytes(bytes);
            case AAAA -> RDataAAAA.from_bytes(bytes);
            case CNAME -> RDataCNAME.from_bytes(bytes);
            case MX -> RDataMX.from_bytes(bytes);
            case NS -> RDataNS.from_bytes(bytes);
            case PTR -> RDataPTR.from_bytes(bytes);
            case SOA -> RDataSOA.from_bytes(bytes);
            case TXT -> RDataTXT.from_bytes(bytes);
        };
    }
}