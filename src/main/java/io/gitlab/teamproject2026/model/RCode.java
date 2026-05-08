package io.gitlab.teamproject2026.model;

public enum RCode {
    NOERROR(0), FORMERR(1), SERVFAIL(2), NXDOMAIN(3), NOTIMP(4), REFUSED(5);

    private final int value;
    RCode(int value) { this.value = value; }
    public int getValue() { return value; }
}