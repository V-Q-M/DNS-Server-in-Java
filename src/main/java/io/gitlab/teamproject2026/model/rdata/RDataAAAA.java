package io.gitlab.teamproject2026.model.rdata;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * Represents a DNS AAAA record.
 * Maps a domain name to an IPv6 address (128-bit).
 */
public class RDataAAAA implements RData {
    public static final int TYPE = 28;

    private byte[] address;

    public byte[] getAddress() { return address; }
    public void setAddress(byte[] address) { this.address = address; }

    /**
     * @return UNCOMPRESSED IPv6 address 
     * seperated by ":" e.g. 2001:07c0:300c:30f0:0000:0000:8602:0501
     * 
     */
    @Override
    public String toString(){
        if (address.length != 16) {
            throw new IllegalArgumentException("IPv6 address must be exactly 16 bytes long, no compression allowed, (found " + address.length + " bytes)");
        }
        String[] twoBytesBlocks = new String[8];
        for (int i = 0; i < 8; i++) {
            twoBytesBlocks[i] = HexFormat.of().formatHex(address, i * 2, (i + 1) * 2);
        }
        return String.join(":", twoBytesBlocks);
    }

    @Override
    public byte[] toBytes() {
        return address;
    }

    public static RDataAAAA fromBytes(byte[] bytes, int byteIndex) {
        RDataAAAA rDataAAAA = new RDataAAAA();
        rDataAAAA.setAddress(Arrays.copyOfRange(bytes, byteIndex, byteIndex + 16));
        return rDataAAAA;
    }
}