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
    byte[] toBytes();

    /**
     * Deserializes a rdata record from its binary DNS wire format.
     *
     * @param bytes COMPLETE raw bytes array of an incoming DNS packet
     * @param rrType the rrType of the resource record from an incoming DNS packet
     * @return a new ResourceRecord instance populated with the parsed data
     */
    static RData fromBytes(byte[] bytes, int byteIndex, RRType rrType) {
        return switch (rrType) {
            case A -> RDataA.fromBytes(bytes, byteIndex);
            case AAAA -> RDataAAAA.fromBytes(bytes, byteIndex);
            case CNAME -> RDataCNAME.fromBytes(bytes, byteIndex);
            case MX -> RDataMX.fromBytes(bytes, byteIndex);
            case NS -> RDataNS.fromBytes(bytes, byteIndex);
            case PTR -> RDataPTR.fromBytes(bytes, byteIndex);
            case SOA -> RDataSOA.fromBytes(bytes, byteIndex);
            case TXT -> RDataTXT.fromBytes(bytes, byteIndex);
        };
    }
}