package io.gitlab.teamproject2026.config.logging;

/**
 * Identifies the category of a log message.
 * <p>
 * Logging categories group related events (e.g. configuration changes or
 * DNS queries) so they can be routed to one or more logging channels.
 */
public enum LoggingCategoryType {
    /** Catch-all category for messages not assigned to another category. */
    DEFAULT,
    /** General server operation such as startup, shutdown, and reloads. */
    GENERAL,
    /** Configuration parsing and zone loading messages. */
    CONFIG,
    /** DNS queries received by the server. */
    QUERIES,
    /** Failed DNS queries and lookup errors. */
    QUERY_ERRORS,
    /** Packet Trace during resolving */
    TRACE,
}