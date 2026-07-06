package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.RRType;

import java.util.Arrays;
import java.util.Base64;

/**
 * Represents a DNS RRSIG record. Stores a digital signature covering a DNS RRset. Contains signature metadata, signer
 * information, and signature bytes.
 */
public record RDataRRSIG(
        int typeCovered,
        int algorithm,
        int labels,
        long originalTtl,
        long signatureExpiration,
        long signatureInception,
        int keyTag,
        String[] signerName,
        byte[] signature)
        implements RData {
    public RDataRRSIG(byte[] bytes, int byteIndex) {
        int typeCovered = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);

        int algorithm = bytes[byteIndex++] & 0xFF;
        int labels = bytes[byteIndex++] & 0xFF;
        long originalTtl =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long signatureExpiration =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
        long signatureInception =
                DNSParser.as32BitInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);

        int keyTag = DNSParser.as16BitInt(bytes[byteIndex++], bytes[byteIndex++]);

        String[] signerName = DNSParser.parseName(bytes, byteIndex);

        byteIndex += DNSParser.countNameLength(bytes, byteIndex);

        byte[] signature = Arrays.copyOfRange(bytes, byteIndex, bytes.length);

        this(
                typeCovered,
                algorithm,
                labels,
                originalTtl,
                signatureExpiration,
                signatureInception,
                keyTag,
                signerName,
                signature);
    }

    @Override
    public int getType() {
        return RRType.RRSIG;
    }

    @Override
    public String toString() {
        return "{\n" + "        typeCovered: "
                + typeCovered + ",\n        algorithm: "
                + algorithm + ",\n        labels: "
                + labels + ",\n        originalTtl: "
                + originalTtl + ",\n        signatureExpiration: "
                + signatureExpiration + ",\n        signatureInception: "
                + signatureInception + ",\n        keyTag: "
                + keyTag + ",\n        signerName: "
                + String.join(".", signerName) + ",\n        signature: "
                + Base64.getEncoder().encodeToString(signature) + "\n    }";
    }

    @Override
    public byte[] toBytes() {
        byte[] signerNameBytes = DNSPacket.encodeName(signerName);

        byte[] result = new byte[18 + signerNameBytes.length + signature.length];

        int pos = 0;

        // Type Covered (2 bytes)
        result[pos] = (byte) (typeCovered >> 8);
        result[pos + 1] = (byte) (typeCovered);
        pos += 2;

        // Algorithm (1 byte)
        result[pos] = (byte) algorithm;
        pos += 1;

        // Labels (1 byte)
        result[pos] = (byte) labels;
        pos += 1;

        // Original TTL (4 bytes)
        result[pos] = (byte) (originalTtl >> 24);
        result[pos + 1] = (byte) (originalTtl >> 16);
        result[pos + 2] = (byte) (originalTtl >> 8);
        result[pos + 3] = (byte) (originalTtl);
        pos += 4;

        // Signature Expiration (4 bytes)
        result[pos] = (byte) (signatureExpiration >> 24);
        result[pos + 1] = (byte) (signatureExpiration >> 16);
        result[pos + 2] = (byte) (signatureExpiration >> 8);
        result[pos + 3] = (byte) (signatureExpiration);
        pos += 4;

        // Signature Inception (4 bytes)
        result[pos] = (byte) (signatureInception >> 24);
        result[pos + 1] = (byte) (signatureInception >> 16);
        result[pos + 2] = (byte) (signatureInception >> 8);
        result[pos + 3] = (byte) (signatureInception);
        pos += 4;

        // Key Tag (2 bytes)
        result[pos] = (byte) (keyTag >> 8);
        result[pos + 1] = (byte) (keyTag);
        pos += 2;

        // Signer Name
        System.arraycopy(signerNameBytes, 0, result, pos, signerNameBytes.length);
        pos += signerNameBytes.length;

        // Signature
        System.arraycopy(signature, 0, result, pos, signature.length);

        return result;
    }

    public int getTypeCovered() {
        return typeCovered;
    }

    public int getAlgorithm() {
        return algorithm;
    }

    public int getLabels() {
        return labels;
    }

    public long getOriginalTtl() {
        return originalTtl;
    }

    public long getSignatureExpiration() {
        return signatureExpiration;
    }

    public long getSignatureInception() {
        return signatureInception;
    }

    public int getKeyTag() {
        return keyTag;
    }

    public String[] getSignerName() {
        return signerName;
    }

    public byte[] getSignature() {
        return signature;
    }
}
