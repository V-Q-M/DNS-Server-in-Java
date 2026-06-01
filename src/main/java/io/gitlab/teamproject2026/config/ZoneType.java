package io.gitlab.teamproject2026.config;
/**
 * PRIMARY == MASTER
 * SECONDARY == SLAVE
 * I kept it for legacy reasons
 */
public enum ZoneType {
    PRIMARY, SECONDARY, MASTER, SLAVE;

    public static ZoneType fromValue(String val){
        return switch (val){
            case "primary" -> PRIMARY;
            case "secondary" -> SECONDARY;
            case "master" -> MASTER;
            case "slave" -> SLAVE;
            default -> throw new IllegalStateException("Unexpected value: " + val);
        };
    }
}
