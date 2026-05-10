package io.gitlab.teamproject2026.model;

public enum RRClass {
    IN(1);

    private final int value;
    RRClass(int value) { this.value = value; }
    public int getValue() { return value; }
    public static RRClass fromValue(int value) {
        for (RRClass rrClass : RRClass.values()) {
            if (rrClass.value == value) {
                return rrClass;
            }
        }
        throw new IllegalArgumentException("Invalid RRClass: " + value);
    }
}