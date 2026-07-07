package io.gitlab.teamproject2026.config.zones;
/**
 * Supported DNS zone types. {@code MASTER} and {@code SLAVE} are retained as legacy aliases for {@code PRIMARY} and
 * {@code SECONDARY}.
 */
public enum ZoneType {
    PRIMARY,
    SECONDARY,
    MASTER,
    SLAVE;

    public static ZoneType fromValue(String val) {
        return switch (val) {
            case "primary" -> PRIMARY;
            case "secondary" -> SECONDARY;
            case "master" -> MASTER;
            case "slave" -> SLAVE;
            default -> throw new IllegalArgumentException("Unexpected value: " + val);
        };
    }
}
