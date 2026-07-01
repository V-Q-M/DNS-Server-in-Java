package io.gitlab.teamproject2026.model.rdata.dnssec;

import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RData;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Represents a DNS NSEC3 record. Provides hashed authenticated denial of existence in DNSSEC. Contains hashing
 * parameters, next hashed owner name, and type bitmaps.
 */
public record RDataNSEC3(
        int hashAlgorithm, int flags, int iterations, byte[] salt, byte[] nextHashedOwnerName, byte[] typeBitMaps)
        implements RData {
    public RDataNSEC3(byte[] bytes, int byteIndex) {
        if (bytes.length < 5) {
            throw new IllegalArgumentException("NSEC3 RDATA too short");
        }
        int hashAlgorithm = (bytes[byteIndex++] & 0xff);
        int flags = (bytes[byteIndex++] & 0xff);
        int iterations = (((bytes[byteIndex++] & 0xff) << 8) | (bytes[byteIndex++] & 0xff));

        int saltLength = bytes[byteIndex++] & 0xff;
        if (byteIndex + saltLength > bytes.length) {
            throw new IllegalArgumentException("Invalid NSEC3 salt length");
        }

        byte[] salt = new byte[saltLength];
        System.arraycopy(bytes, byteIndex, salt, 0, saltLength);
        byteIndex += saltLength;

        if (byteIndex >= bytes.length) {
            throw new IllegalArgumentException("Missing NSEC3 hash length");
        }

        int hashLength = bytes[byteIndex++] & 0xff;
        if (byteIndex + hashLength > bytes.length) {
            throw new IllegalArgumentException("Invalid NSEC3 hash length");
        }

        byte[] nextHashedOwnerName = new byte[hashLength];
        System.arraycopy(bytes, byteIndex, nextHashedOwnerName, 0, hashLength);
        byteIndex += hashLength;

        byte[] typeBitMaps = new byte[bytes.length - byteIndex];
        System.arraycopy(bytes, byteIndex, typeBitMaps, 0, typeBitMaps.length);

        this(hashAlgorithm, flags, iterations, salt, nextHashedOwnerName, typeBitMaps);
    }

    @Override
    public RRType getType() {
        return RRType.NSEC3;
    }

    public int getHashAlgorithm() {
        return this.hashAlgorithm;
    }

    public int getFlags() {
        return this.flags;
    }

    public int getIterations() {
        return this.iterations;
    }

    public byte[] getSalt() {
        return this.salt;
    }

    public byte[] getNextHashedOwnerName() {
        return this.nextHashedOwnerName;
    }

    public byte[] getTypeBitMaps() {
        return this.typeBitMaps;
    }

    @Override
    public String toString() {
        return "{\n" + "        hashAlgorithm: "
                + hashAlgorithm + ",\n        flags: "
                + flags + ",\n        iterations: "
                + iterations + ",\n        salt: "
                + HexFormat.of().formatHex(salt) + ",\n        nextHashedOwnerName: "
                + Base64.getEncoder().encodeToString(nextHashedOwnerName) + ",\n        typeBitMaps: "
                + HexFormat.of().formatHex(typeBitMaps) + "\n    }";
    }

    @Override
    public byte[] toBytes() {
        byte[] result = new byte[5 + salt.length + 1 + nextHashedOwnerName.length + typeBitMaps.length];

        int pos = 0;
        result[pos++] = (byte) hashAlgorithm;
        result[pos++] = (byte) flags;

        result[pos] = (byte) (iterations >> 8);
        result[pos + 1] = (byte) iterations;
        pos += 2;

        result[pos++] = (byte) salt.length;

        System.arraycopy(salt, 0, result, pos, salt.length);
        pos += salt.length;

        result[pos++] = (byte) nextHashedOwnerName.length;

        System.arraycopy(nextHashedOwnerName, 0, result, pos, nextHashedOwnerName.length);
        pos += nextHashedOwnerName.length;

        System.arraycopy(typeBitMaps, 0, result, pos, typeBitMaps.length);

        return result;
    }
}
