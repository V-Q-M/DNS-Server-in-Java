package io.gitlab.teamproject2026.model;

public enum OpCode {
    QUERY(0), IQUERY(1), STATUS(2);

    private final int value;
    OpCode(int value) { this.value = value; }
    public int getValue() { return value; }
}