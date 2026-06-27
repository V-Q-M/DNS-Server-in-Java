package io.gitlab.teamproject2026.config.logging;


// Note, we dont have to use all of them
public enum LoggingSeverity {
    CRITICAL,
    ERROR,
    WARNING,
    NOTICE,
    INFO,
    DEBUG,
    DYNAMIC;
    public static LoggingSeverity fromValue(String value) {
        return LoggingSeverity.valueOf(value.toUpperCase());
    }
}