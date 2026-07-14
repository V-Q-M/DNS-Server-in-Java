package io.gitlab.teamproject2026.model.rdata;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.RRType;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Represents a DNS NSEC record. Provides authenticated denial of existence in DNSSEC. Lists the next domain name and
 * existing RR types for an owner name.
 */
public record RDataNSEC(String[] nextDomainName, byte[] typeBitMaps) implements RData {
    public RDataNSEC(byte[] bytes) throws IndexOutOfBoundsException, IllegalArgumentException {
        int byteIndex = 0;
        if (bytes.length == 0) {
            throw new IllegalArgumentException("NSEC RDATA cannot be empty");
        }

        String[] nextDomainName = (DNSParser.parseName(bytes, byteIndex));
        byteIndex += DNSParser.countNameLength(bytes, byteIndex);
        byte[] typeBitMaps = Arrays.copyOfRange(bytes, byteIndex, bytes.length);

        this(nextDomainName, typeBitMaps);
    }

    @Override
    public int getType() {
        return RRType.NSEC;
    }

    public String[] getNextDomainName() {
        return this.nextDomainName;
    }

    public byte[] getTypeBitMaps() {
        return typeBitMaps;
    }

    @Override
    public String toString() {
        return "{\n" + "        nextDomainName: "
                + String.join(".", nextDomainName) + ",\n        typeBitMaps: "
                + HexFormat.of().formatHex(typeBitMaps) + "\n   }";
    }

    @Override
    public byte[] toBytes() {
        byte[] nextDomainNameBytes = DNSPacket.encodeName(nextDomainName);

        byte[] result = new byte[nextDomainNameBytes.length + 2 + typeBitMaps.length];

        int pos = 0;
        System.arraycopy(nextDomainNameBytes, 0, result, pos, nextDomainNameBytes.length);
        pos += nextDomainNameBytes.length;

        result[pos++] = 0; // window block 0
        result[pos++] = (byte) typeBitMaps.length;

        System.arraycopy(typeBitMaps, 0, result, pos, typeBitMaps.length);

        return result;
    }
}
