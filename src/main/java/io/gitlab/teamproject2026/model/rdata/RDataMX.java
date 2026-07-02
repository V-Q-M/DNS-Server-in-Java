package io.gitlab.teamproject2026.model.rdata;

import static io.gitlab.teamproject2026.model.DNSPacket.encodeName;

import io.gitlab.teamproject2026.DNSParser;
import io.gitlab.teamproject2026.model.RRType;

/** Represents a DNS MX (Mail Exchange) record. Specifies the mail server responsible for a domain. */
public class RDataMX implements RData {
    private int preference;
    private String[] exchange;

    public RDataMX() {}

    public RDataMX(int preference, String[] exchange) {
        this.preference = preference;
        this.exchange = exchange;
    }

    @Override
    public int getType() {
        return RRType.MX;
    }

    public int getPreference() {
        return preference;
    }

    public void setPreference(int preference) {
        this.preference = preference;
    }

    public String[] getExchange() {
        return exchange;
    }

    public void setExchange(String[] exchange) {
        this.exchange = exchange;
    }

    @Override
    public String toString() {
        return "preference: " + preference + ", exchange: " + String.join(".", exchange) + ".";
    }

    @Override
    public byte[] toBytes() {
        byte[] name = encodeName(exchange);
        byte[] result = new byte[2 + name.length];
        result[0] = (byte) (preference >> 8);
        result[1] = (byte) (preference);
        System.arraycopy(name, 0, result, 2, name.length);
        return result;
    }

    public static RDataMX fromBytes(byte[] bytes, int byteIndex) {
        int preference = DNSParser.as16BitInt(bytes[byteIndex], bytes[1 + byteIndex]);
        String[] exchange = DNSParser.parseName(bytes, 2 + byteIndex);
        return new RDataMX(preference, exchange);
    }
}
