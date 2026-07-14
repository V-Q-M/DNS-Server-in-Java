package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.model.RRType;
import java.util.HexFormat;

/**
 * Represents a DNS NSEC3PARAM record. Stores parameters used for NSEC3 hashing in a DNSSEC zone. Includes hash
 * algorithm, flags, iterations, and salt value.
 */
public record RDataNSEC3PARAM(int hashAlgorithm, int flags, int iterations, byte[] salt) implements RData {
    public RDataNSEC3PARAM(byte[] bytes) throws IllegalArgumentException {
        int byteIndex = 0;
        if (bytes.length < 5) {
            throw new IllegalArgumentException("NSEC3PARAM RDATA too short");
        }
        int hashAlgorithm = (bytes[byteIndex++] & 0xFF);
        int flags = (bytes[byteIndex++] & 0xFF);

        int iterations = (((bytes[byteIndex++] & 0xFF) << 8) | (bytes[byteIndex++] & 0xFF));

        int saltLength = bytes[byteIndex++] & 0xFF;
        if (byteIndex + saltLength > bytes.length) {
            throw new IllegalArgumentException("Invalid NSEC3PARAM salt length");
        }

        byte[] salt = new byte[saltLength];
        System.arraycopy(bytes, byteIndex, salt, 0, saltLength);

        this(hashAlgorithm, flags, iterations, salt);
    }

    @Override
    public int getType() {
        return RRType.NSEC3PARAM;
    }

    public int getHashAlgorithm() {
        return hashAlgorithm;
    }

    public int getFlags() {
        return flags;
    }

    public int getIterations() {
        return iterations;
    }

    public byte[] getSalt() {
        return salt;
    }

    @Override
    public String toString() {
        return "{\n" + "        hashAlgorithm: "
                + hashAlgorithm + ",\n        flags: "
                + flags + ",\n        iterations: "
                + iterations + ",\n        salt: "
                + HexFormat.of().formatHex(salt) + "\n    }";
    }

    @Override
    public byte[] toBytes() {
        byte[] result = new byte[5 + salt.length];
        int pos = 0;

        result[pos++] = (byte) hashAlgorithm;
        result[pos++] = (byte) flags;

        result[pos] = (byte) (iterations >> 8);
        result[pos + 1] = (byte) iterations;
        pos += 2;

        result[pos++] = (byte) salt.length;
        System.arraycopy(salt, 0, result, pos, salt.length);
        return result;
    }
}
