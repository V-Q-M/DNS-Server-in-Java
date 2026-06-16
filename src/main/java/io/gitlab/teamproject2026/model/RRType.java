package io.gitlab.teamproject2026.model;

public enum RRType {
    A(1), NS(2), CNAME(5), SOA(6), PTR(12),MX(15), TXT(16), AAAA(28), DS(43),  RRSIG(46), NSEC(47), DNSKEY(48), NSEC3(50), NSEC3PARAM(51), UNKNOWN(-1);

    private final int value;
    RRType(int value) { this.value = value; }
    public int getValue() { return value; }
    public static RRType fromValue(int value) {
        for (RRType rrType : RRType.values()) {
            if (rrType.value == value) {
                return rrType;
            }
        }
        throw new IllegalArgumentException("Invalid RRType: " + value);
    }
}