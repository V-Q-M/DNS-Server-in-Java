package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;
import java.util.Base64;

/**
 * Represents a DNS DNSKEY record. Stores a public key used for DNSSEC validation. Contains key flags, protocol,
 * algorithm, and public key material.
 */
public record RDataDNSKEY(int flags, int protocol, int algorithm, byte[] publickey) implements RData {
    public RDataDNSKEY(byte[] bytes) throws IllegalArgumentException {
        if (bytes.length < 4) {
            throw new IllegalArgumentException("DNSKEY RDATA must be at least 4 bytes");
        }
        int flags = DNSParser.as16BitInt(bytes[0], bytes[1]);

        int protocol = (bytes[2] & 0xff);
        int algorithm = (bytes[3] & 0xff);

        byte[] publicKey = new byte[bytes.length - 4];
        System.arraycopy(bytes, 4, publicKey, 0, publicKey.length);

        this(flags, protocol, algorithm, publicKey);
    }

    @Override
    public int getType() {
        return RRType.DNSKEY;
    }

    public int getFlags() {
        return this.flags;
    }

    public int getProtocol() {
        return this.protocol;
    }

    public int getAlgorithm() {
        return this.algorithm;
    }

    public byte[] getPublickey() {
        return this.publickey;
    }

    @Override
    public String toString() {
        return "{\n" + "        flags: "
                + flags + ",\n        protocol: "
                + protocol + ",\n        algorithm: "
                + algorithm + ",\n        publicKey: "
                + Base64.getEncoder().encodeToString(publickey) + "\n    }";
    }

    @Override
    public byte[] toBytes() {
        byte[] publicKey = this.getPublickey();
        byte[] result = new byte[publicKey.length + 4];
        int pos = 0;

        result[pos] = (byte) (flags >> 8);
        result[pos + 1] = (byte) (flags);
        pos += 2;

        result[pos] = (byte) protocol;
        pos += 1;

        result[pos] = (byte) algorithm;
        pos += 1;

        System.arraycopy(publicKey, 0, result, pos, publicKey.length);

        return result;
    }
}
