package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

import java.util.HexFormat;

/**
 * Represents a DNS DS (Delegation Signer) record. Stores a hash of a child zone DNSKEY record. Used to establish the
 * chain of trust in DNSSEC.
 */
public record RDataDS(int keyTag, int algorithm, int digestType, byte[] digest) implements RData {
    public RDataDS(byte[] bytes, int byteIndex) {
        if (bytes.length < 4) {
            throw new IllegalArgumentException("DS RDATA must be at least 4 bytes");
        }
        int keyTag = (DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]));

        int algorithm = (bytes[byteIndex++] & 0xFF);
        int digestType = (bytes[byteIndex++] & 0xFF);

        byte[] digest = new byte[bytes.length - byteIndex];
        System.arraycopy(bytes, byteIndex, digest, 0, digest.length);

        this(keyTag, algorithm, digestType, digest);
    }

    @Override
    public int getType() {
        return RRType.DS;
    }

    public int getKeyTag() {
        return this.keyTag;
    }

    public int getAlgorithm() {
        return this.algorithm;
    }

    public int getDigestType() {
        return this.digestType;
    }

    public byte[] getDigest() {
        return this.digest;
    }

    @Override
    public String toString() {
        return "{\n" + "        keyTag: "
                + keyTag + ",\n        algorithm: "
                + algorithm + ",\n        digestType: "
                + digestType + ",\n        digest: "
                + HexFormat.of().formatHex(digest) + "\n    }";
    }

    @Override
    public byte[] toBytes() {
        byte[] result = new byte[4 + digest.length];
        int pos = 0;

        result[pos] = (byte) (keyTag >> 8);
        result[pos + 1] = (byte) keyTag;
        pos += 2;

        result[pos++] = (byte) algorithm;
        result[pos++] = (byte) digestType;

        System.arraycopy(digest, 0, result, pos, digest.length);

        return result;
    }
}
