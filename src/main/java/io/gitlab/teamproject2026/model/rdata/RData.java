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
     * @param bytes raw bytes from an incoming DNS packet
     * @param rrType the rrType of the resource record from an incoming DNS packet
     * @return a new ResourceRecord instance populated with the parsed data
     */
    static RData fromBytes(byte[] bytes, RRType rrType) {
        return switch (rrType) {
            case A -> RDataA.fromBytes(bytes);
            case AAAA -> RDataAAAA.fromBytes(bytes);
            case CNAME -> RDataCNAME.fromBytes(bytes);
            case MX -> RDataMX.fromBytes(bytes);
            case NS -> RDataNS.fromBytes(bytes);
            case PTR -> RDataPTR.fromBytes(bytes);
            case SOA -> RDataSOA.fromBytes(bytes);
            case TXT -> RDataTXT.fromBytes(bytes);
        };
    }
}