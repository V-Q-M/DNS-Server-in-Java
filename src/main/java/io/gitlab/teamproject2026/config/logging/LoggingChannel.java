package io.gitlab.teamproject2026.config.logging;

/**
 * Represents a logging channel defined in the {@code logging} section.
 * <p>
 * A logging channel defines where log messages are written, the minimum
 * severity required for a message to be recorded, and which metadata should
 * be included in each log entry. Multiple logging categories may reference
 * the same channel.
 *
 * @param channelName the unique name of the logging channel
 * @param fileName the path of the log file associated with the channel
 * @param severity the minimum severity required for a message to be logged
 * @param printTime whether timestamps should be included in log entries
 * @param printCategory whether the logging category should be included in log entries
 * @param printSeverity whether the message severity should be included in log entries
 */
public record LoggingChannel(
        String channelName,
        String fileName,
        LoggingSeverity severity,
        boolean printTime,
        boolean printCategory,
        boolean printSeverity
) {
}