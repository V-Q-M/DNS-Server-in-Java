package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import java.util.Arrays;

/** Interface for all DNS RDATA types. Each implementing class defines its own type-specific fields. */
public sealed interface RData
        permits RDataA,
                RDataAAAA,
                RDataCNAME,
                RDataDNSKEY,
                RDataDS,
                RDataMX,
                RDataNS,
                RDataNSEC,
                RDataNSEC3,
                RDataNSEC3PARAM,
                RDataOPTOption,
                RDataPTR,
                RDataRRSIG,
                RDataSOA,
                RDataTXT,
                RDataUnknown {

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
     * @throws IllegalArgumentException if RData exceeds packet bounds or if any of the RData Sections are malformed
     * @return a new ResourceRecord instance populated with the parsed data
     */
    static RData fromBytes(byte[] bytes, int byteIndex, int rdlength, int rrType) throws IllegalArgumentException {
        if (byteIndex < 0 || rdlength < 0 || byteIndex + rdlength > bytes.length) {
            throw new IllegalArgumentException("RDATA exceeds packet bounds");
        }
        byte[] rdata = Arrays.copyOfRange(bytes, byteIndex, byteIndex + rdlength);
        try {
            return switch (rrType) {
                // These can use the complete packet for decompression
                case RRType.A -> RDataA.fromBytes(bytes, byteIndex);
                case RRType.AAAA -> RDataAAAA.fromBytes(bytes, byteIndex);
                case RRType.CNAME -> RDataCNAME.fromBytes(bytes, byteIndex);
                case RRType.MX -> RDataMX.fromBytes(bytes, byteIndex);
                case RRType.NS -> RDataNS.fromBytes(bytes, byteIndex);
                case RRType.PTR -> RDataPTR.fromBytes(bytes, byteIndex);
                case RRType.SOA -> RDataSOA.fromBytes(bytes, byteIndex);
                case RRType.TXT -> RDataTXT.fromBytes(bytes, byteIndex, rdlength);
                case RRType.OPT -> new RDataOPTOption(bytes, byteIndex, rdlength);
                case RRType.RRSIG -> new RDataRRSIG(bytes, byteIndex, rdlength);

                // These don't need the complete packet
                case RRType.DS -> new RDataDS(rdata);
                case RRType.NSEC -> new RDataNSEC(rdata);
                case RRType.NSEC3 -> new RDataNSEC3(rdata);
                case RRType.NSEC3PARAM -> new RDataNSEC3PARAM(rdata);
                case RRType.DNSKEY -> new RDataDNSKEY(rdata);
                default -> new RDataUnknown(rrType, bytes, byteIndex, rdlength);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Malformed RData Section");
        }
    }
}
