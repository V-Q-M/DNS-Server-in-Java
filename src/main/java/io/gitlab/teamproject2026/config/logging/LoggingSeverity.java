package io.gitlab.teamproject2026.config.logging;

/**
 * Represents the supported logging severity levels.
 * <p>
 * The values correspond to BIND's logging severities. While this project
 * may not use every severity level, they are provided for compatibility
 * with the configuration format.
 */
public enum LoggingSeverity {
    /** Critical failures that may prevent normal server operation. */
    CRITICAL,
    /** Errors that affect functionality but are not fatal. */
    ERROR,
    /** Warnings about potential problems or unexpected conditions. */
    WARNING,
    /** Significant normal events. */
    NOTICE,
    /** General informational messages. */
    INFO,
    /** Verbose debugging output. */
    DEBUG,
    /** Uses the server's current debug level dynamically. */
    DYNAMIC;


    public static LoggingSeverity fromValue(String value) {
        return LoggingSeverity.valueOf(value.toUpperCase());
    }
}