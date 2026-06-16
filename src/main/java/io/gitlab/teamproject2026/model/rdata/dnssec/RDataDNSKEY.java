package io.gitlab.teamproject2026.model.rdata.dnssec;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RData;

import java.util.Base64;

/**
 * Represents a DNS DNSKEY record.
 * Stores a public key used for DNSSEC validation.
 * Contains key flags, protocol, algorithm, and public key material.
 */
public record RDataDNSKEY(long flags, long protocol, long algorithm, byte[] publickey) implements RData {
    public RDataDNSKEY(byte[] bytes, int byteIndex) {
        if (bytes.length < 4) {
            throw new IllegalArgumentException("DNSKEY RDATA must be at least 4 bytes");
        }
        long flags = DNSParser.as16BitInt(bytes[byteIndex++],bytes[byteIndex++]);

        long protocol = (bytes[byteIndex++] & 0xff);
        long algorithm = (bytes[byteIndex++] & 0xff);

        byte[] publicKey = new byte[bytes.length - byteIndex];
        System.arraycopy(bytes, byteIndex, publicKey, 0, publicKey.length);

        if (bytes.length < 4) {
            throw new IllegalArgumentException("DNSKEY RDATA must be at least 4 bytes");
        }

        this(flags, protocol, algorithm, publicKey);
    }

    @Override
    public RRType getType() {
        return RRType.DNSKEY;
    }

    public long getFlags(){ return this.flags; }
    public long getProtocol() { return this.protocol; }
    public long getAlgorithm() { return this.algorithm; }
    public byte[] getPublickey() { return  this.publickey; }

    @Override
    public String toString() {
        return  "{\n" +
                "        flags: " + flags +
                ",\n        protocol: " + protocol +
                ",\n        algorithm: " + algorithm +
                ",\n        publicKey: " + Base64.getEncoder().encodeToString(publickey) +
                "\n    }";
    }


    @Override
    public byte[] toBytes() {
        byte[] publicKey = this.getPublickey();
        byte[] result = new byte[publicKey.length + 4];
        int pos = 0;

        result[pos] = (byte) (flags >> 8);
        result[pos+1] = (byte) (flags);
        pos+=2;

        result[pos] = (byte) protocol;
        pos+=1;

        result[pos] = (byte) algorithm;
        pos+=1;

        System.arraycopy(publicKey, 0, result, pos, publicKey.length);

        return result;
    }
}
