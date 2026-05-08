package io.gitlab.teamproject2026.model;

public enum RRClass {
    IN(1);

    private final int value;
    RRClass(int value) { this.value = value; }
    public int getValue() { return value; }
}