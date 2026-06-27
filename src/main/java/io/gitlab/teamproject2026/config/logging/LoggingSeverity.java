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
    CRITICAL(0),
    /** Errors that affect functionality but are not fatal. */
    ERROR(1),
    /** Warnings about potential problems or unexpected conditions. */
    WARNING(2),
    /** Significant normal events. */
    NOTICE(3),
    /** General informational messages. */
    INFO(4),
    /** Verbose debugging output. */
    DEBUG(5),
    /** Uses the server's current debug level dynamically. */
    DYNAMIC(6);

    private final int level;
    LoggingSeverity(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric severity level.
     *
     * @return the severity level, where lower values indicate more severe
     *         messages
     */
    public int level() {
        return level;
    }

    public static LoggingSeverity fromValue(String value) {
        return LoggingSeverity.valueOf(value.toUpperCase());
    }
}