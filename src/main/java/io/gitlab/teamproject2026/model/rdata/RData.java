package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.dnssec.*;

/** Interface for all DNS RDATA types. Each implementing class defines its own type-specific fields. */
public interface RData {

    /**
     * Predefined type of the rdata and thus of the ResourceRecord logically identical to testing the instance of the
     * RData
     *
     * @return rrtype of the RData and thus of the ResourceRecord
     */
    int getType();

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
    static RData fromBytes(byte[] bytes, int byteIndex, int rdlength, int rrType) {
        return switch (rrType) {
            case RRType.A -> RDataA.fromBytes(bytes, byteIndex);
            case RRType.AAAA -> RDataAAAA.fromBytes(bytes, byteIndex);
            case RRType.CNAME -> RDataCNAME.fromBytes(bytes, byteIndex);
            case RRType.MX -> RDataMX.fromBytes(bytes, byteIndex);
            case RRType.NS -> RDataNS.fromBytes(bytes, byteIndex);
            case RRType.PTR -> RDataPTR.fromBytes(bytes, byteIndex);
            case RRType.SOA -> RDataSOA.fromBytes(bytes, byteIndex);
            case RRType.TXT -> RDataTXT.fromBytes(bytes, byteIndex, rdlength);
            case RRType.OPT -> new RDataOPTOption(bytes, byteIndex, rdlength);
            case RRType.DS -> new RDataDS(bytes, byteIndex);
            case RRType.RRSIG -> new RDataRRSIG(bytes, byteIndex);
            case RRType.NSEC -> new RDataNSEC(bytes, byteIndex);
            case RRType.NSEC3 -> new RDataNSEC3(bytes, byteIndex);
            case RRType.NSEC3PARAM -> new RDataNSEC3PARAM(bytes, byteIndex);
            case RRType.DNSKEY -> new RDataDNSKEY(bytes, byteIndex);
            default -> new RDataUnknown(rrType, bytes, byteIndex, rdlength);
        };
    }
}
