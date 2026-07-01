package io.gitlab.teamproject2026.model;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.rdata.RDataOPTOption;

/**
 * Represents the Answer Section of a DNS packet. Contains the domain name, class, TTL, data length and the actual
 * resource record data.
 */
public class OPTPseudoResourceRecord extends ResourceRecord {

    private int requestedPayloadSize;
    private long flags;

    public OPTPseudoResourceRecord(byte[] bytes, int byteIndex) {
        // gets called during ResourceRecord::fromBytes, so byteIndex is already advanced past the Type
        this.name = new String[] {}; // Must be 0 (root domain) according to RFC 6891 6.1.2
        this.requestedPayloadSize = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        this.flags =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        int rdlengthComp = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);
        this.rdata = new RDataOPTOption(bytes, byteIndex);
        this.lastProcessedByte = byteIndex + rdlengthComp;
    }

    public long getFlags() {
        return flags;
    }

    public int getRequestedPayloadSize() {
        return requestedPayloadSize;
    }

    public void setRequestedPayloadSize(int requestedPayloadSize) {
        this.requestedPayloadSize = requestedPayloadSize;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    /**
     * Due to this being a Pseudo Record, additional flags are stored instead of ttl, do not handle this as time
     *
     * @return unsigned 32 bit "int" of flags
     */
    @Override
    public long getTtl() {
        return flags;
    }

    @Override
    public String toString() {
        return "\n    name: " + String.join(".", this.name) + "." + "\n"
                + "    rrtype: " + rdata.getType() + "\n"
                + "    requestedPayloadSize: " + requestedPayloadSize + "\n"
                + "    flags: " + flags + "s\n"
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
        result[pos++] = (byte) (this.getType().getValue() >> 8);
        result[pos++] = (byte) (this.getType().getValue()); // 2 Part RType

        result[pos++] = (byte) (this.getRequestedPayloadSize() >> 8);
        result[pos++] = (byte) (this.getRequestedPayloadSize()); // 2 Part requestedPayloadSize

        result[pos++] = (byte) (this.getFlags() >> 24);
        result[pos++] = (byte) (this.getFlags() >> 16);
        result[pos++] = (byte) (this.getFlags() >> 8);
        result[pos++] = (byte) (this.getFlags()); // 4 Part Flags

        result[pos++] = (byte) (rdataBytes.length >> 8);
        result[pos++] = (byte) (rdataBytes.length); // 2 Part Rdata Length

        System.arraycopy(rdataBytes, 0, result, pos, rdataBytes.length);

        return result;
    }
}
