package io.gitlab.teamproject2026.model.rdata;

import java.util.Arrays;

/**
 * Represents a DNS A record.
 * Maps a domain name to an IPv4 address (32-bit).
 */
public class RDataA implements RData {
    public static final int TYPE = 1;

    private byte[] address;

    public byte[] getAddress() { return address; }
    public void setAddress(byte[] address) { this.address = address; }

    /**
     * @return the Ipv4 address as UNSIGNED decimal
     */
    @Override
    public String toString(){
        if (this.address == null || this.address.length < 4) {
        return "0.0.0.0";
        }
        return (this.address[0] & 0xFF) + "." +
            (this.address[1] & 0xFF) + "." +
            (this.address[2] & 0xFF) + "." +
            (this.address[3] & 0xFF);
    }

    @Override
    public byte[] toBytes() {
        return address;
    }

    public static RDataA fromBytes(byte[] bytes, int byteIndex) {
        RDataA rDataA = new RDataA();
        rDataA.setAddress(Arrays.copyOfRange(bytes, byteIndex, byteIndex + 4));
        return rDataA;
    }
}