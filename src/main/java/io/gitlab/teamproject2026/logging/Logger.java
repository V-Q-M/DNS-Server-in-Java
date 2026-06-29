package io.gitlab.teamproject2026.logging;

import io.gitlab.teamproject2026.config.logging.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Routes log messages to the configured logging channels based on their
 * category and severity. Log output is formatted according to the channel
 * configuration and written to the associated log files.
 */
public class Logger {

    private final LoggingSection logging;
    private final Map<String, BufferedWriter> writers = new HashMap<>();

    // Override the configured logging threshold (e.g. via a CLI flag).
    private LoggingSeverity overwriteSeverity = null;

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    /**
     * Creates a logger using the provided logging configuration.
     * <p>
     * Opens all configured log files and keeps their writers open for the
     * lifetime of the logger to avoid repeatedly opening and closing files.
     *
     * @param logging the parsed logging configuration
     */
    public Logger(LoggingSection logging) {
        this.logging = logging;
        for (LoggingChannel channel : logging.channels()) {
            try {
                Path path = Path.of(channel.fileName());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                writers.put( channel.channelName(),
                        Files.newBufferedWriter(
                                path,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                        )
                );
            } catch (IOException e) {
                System.err.println("Failed to open log file: " + channel.fileName());
            }
        }
    }

    /**
     * Overrides the configured logging severity for all channels.
     * <p>
     * When set, this threshold is used instead of each channel's configured
     * severity. Passing {@code null} disables the override.
     *
     * @param severity the global logging threshold, or {@code null} to disable
     *                 the override
     */
    public void overwriteSeverity(LoggingSeverity severity) {
        this.overwriteSeverity = severity;
    }

    /**
     * Logs a message without echoing it to the console.
     */
    public void log(String message, LoggingCategoryType categoryType, LoggingSeverity severity){
        log(message, categoryType, severity, false);
    }

    /**
     * Logs a message to every channel associated with the given category whose
     * configured severity allows it. Optionally echoes the original message to
     * the console.
     */
    public void log(String message, LoggingCategoryType categoryType, LoggingSeverity severity, boolean alsoPrintToConsole) {
        LoggingCategory category = logging.categories().get(categoryType);
        if (category == null) { return; }

        // Use the global severity override if one has been configured.
        LoggingSeverity effectiveSeverity = overwriteSeverity != null ? overwriteSeverity : severity;

        for (LoggingChannel channel : category.channels()) {
            if (effectiveSeverity.level() <= channel.severity().level()) {
                write(channel, message, categoryType, severity, alsoPrintToConsole);
            }
        }
    }

    /**
     * Formats and writes a single log entry to the specified logging channel.
     * ANSI escape sequences are removed before writing to the log file, while
     * console output retains the original formatting.
     */
    private void write(LoggingChannel channel, String message, LoggingCategoryType categoryType, LoggingSeverity severity, boolean alsoPrintToConsole) {
        BufferedWriter writer = writers.get(channel.channelName());
        if (writer == null) { return; }
        try {
            StringBuilder line = new StringBuilder();
            if (channel.printTime()) {
                line.append('[')
                        .append(LocalDateTime.now().format(LOG_TIME))
                        .append("] ");
            }
            if (channel.printCategory()) {
                line.append('[')
                        .append(categoryType)
                        .append("] ");
            }
            if (channel.printSeverity()) {
                line.append('[')
                        .append(severity)
                        .append("] ");
            }

            // Strip ANSI escape sequences before writing to the log file.
            String cleanedMessage = ANSI_PATTERN.matcher(message).replaceAll("");
            line.append(cleanedMessage);
            writer.write(line.toString());
            writer.newLine();
            writer.flush();

            // Optionally echo the original (ANSI-colored) message to the console.
            if (alsoPrintToConsole){
                if (severity.level() <= LoggingSeverity.WARNING.level()) {
                    System.err.println(message);
                } else {
                    IO.println(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + channel.fileName());
        }
    }

    /**
     * Logs a Trace log message
     * @param message the log message
     */
    public void logTrace(String message){
        log(message, LoggingCategoryType.TRACE, LoggingSeverity.DEBUG);
    }
}