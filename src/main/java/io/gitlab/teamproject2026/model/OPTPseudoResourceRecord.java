package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.rdata.RDataOPTOption;

import java.util.Arrays;

/**
 * Represents the Answer Section of a DNS packet. Contains the domain name, class, TTL, data length and the actual
 * resource record data.
 */
public class OPTPseudoResourceRecord extends ResourceRecord {

    private int requestedPayloadSize, extendedRcode, version;
    private boolean DNSsecOK;
    private byte[] z;

    public OPTPseudoResourceRecord(byte[] bytes, int byteIndex) {
        // gets called during ResourceRecord::fromBytes, so byteIndex is already advanced past the Type
        this.name = new String[] {}; // Must be 0 (root domain) according to RFC 6891 6.1.2
        this.requestedPayloadSize = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        this.extendedRcode = bytes[byteIndex++];
        this.version = bytes[byteIndex++];
        this.DNSsecOK = DNSParser.extractBit(bytes[byteIndex], 0); // DO bit
        this.z = new byte[]{DNSParser.subBits(bytes[byteIndex++], 1, 8), bytes[byteIndex++]}; // remaining 15 bits unused
        int rdlengthComp = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        this.rdata = new RDataOPTOption(bytes, byteIndex, rdlengthComp);
        this.lastProcessedByte = byteIndex + rdlengthComp;
    }

    public int getExtendedRcode() {
        return extendedRcode;
    }
    public void setExtendedRcode(int extendedRcode) {
        this.extendedRcode = extendedRcode;
    }
    public int getVersion() {
        return version;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    public boolean getDNSSecOK() {
        return DNSsecOK;
    }
    public void setDNSSecOK(boolean DNSsecOK) {
        this.DNSsecOK =  DNSsecOK;
    }

    public byte[] getZ() {
        return z;
    }
    public void setZ(byte[] z) {
        this.z = z;
    }
    public int getRequestedPayloadSize() {
        return requestedPayloadSize;
    }

    public void setRequestedPayloadSize(int requestedPayloadSize) {
        this.requestedPayloadSize = requestedPayloadSize;
    }

    @Override
    public String toString() {
        return "\n    name: " + String.join(".", this.name) + "." + "\n"
                + "    rrtype: " + rdata.getType() + "\n"
                + "    requestedPayloadSize: " + requestedPayloadSize + "\n"
                + "    extendedRcode: " + extendedRcode + "\n"
                + "    version: " + version + "\n"
                + "    DNSsecOK: " + DNSsecOK + "\n"
                + "    z: " + Arrays.toString(z) + "\n"
                + "    rdlength: " + rdata.toBytes().length + "\n"
                + "    rdata: " + rdata + "\n";
    }

    /**
     * Serializes this answer section into DNS wire format.
     *
     * @return byte array representing this answer section
     */
    public byte[] toBytes() {
        byte[] rdataBytes = rdata.toBytes();
        byte[] result = new byte[11 + rdataBytes.length];
        int pos = 0;

        result[pos++] = 0x00; // 0 length byte for name labels
        result[pos++] = (byte) (this.getType() >> 8);
        result[pos++] = (byte) (this.getType()); // 2 Part RType

        result[pos++] = (byte) (this.getRequestedPayloadSize() >> 8);
        result[pos++] = (byte) (this.getRequestedPayloadSize()); // 2 Part requestedPayloadSize

        result[pos++] = (byte) (this.getExtendedRcode());
        result[pos++] = (byte) (this.getVersion());
        result[pos++] = (byte) ((this.DNSsecOK ? 1 : 0) << 7 | this.getZ()[0]);
        result[pos++] = this.getZ()[1];

        result[pos++] = (byte) (rdataBytes.length >> 8);
        result[pos++] = (byte) (rdataBytes.length); // 2 Part Rdata Length

        System.arraycopy(rdataBytes, 0, result, pos, rdataBytes.length);

        return result;
    }
}
