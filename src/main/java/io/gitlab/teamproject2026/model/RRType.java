package io.gitlab.teamproject2026.model;

public enum RRType {
    A(1), NS(2), CNAME(5), SOA(6), MX(15), TXT(16), AAAA(28), PTR(12);

    private final int value;
    RRType(int value) { this.value = value; }
    public int getValue() { return value; }
}