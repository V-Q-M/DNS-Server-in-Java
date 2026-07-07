package io.gitlab.teamproject2026.logging;

import static io.gitlab.teamproject2026.AnsiColors.PINK;
import static io.gitlab.teamproject2026.AnsiColors.RESET;

import io.gitlab.teamproject2026.config.logging.*;
import io.gitlab.teamproject2026.model.DNSPacket;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Routes log messages to the configured logging channels based on their category and severity. Log output is formatted
 * according to the channel configuration and written to the associated log files.
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
     *
     * <p>Opens all configured log files and keeps their writers open for the lifetime of the logger to avoid repeatedly
     * opening and closing files.
     *
     * @param logging the parsed logging configuration
     */
    public Logger(LoggingSection logging) {
        this.logging = logging;

        for (LoggingChannel channel : logging.channels()) {
            if (channel.fileName() == null || channel.fileName().isBlank()) {
                continue;
            }

            try {
                writers.put(channel.channelName(), openWriter(channel.fileName(), true));
            } catch (IOException e) {
                System.err.println("Failed to open log file: " + channel.fileName());
            }
        }
    }

    /**
     * Opens a buffered writer for the given log file.
     *
     * <p>The parent directory is created automatically if it does not already exist. When {@code append} is true, new
     * log messages are appended to the existing file. When {@code append} is false, the file is truncated and logging
     * starts from an empty file.
     *
     * @param fileName the path of the log file
     * @param append whether to append to an existing file or truncate it
     * @return a buffered writer for the log file
     * @throws IOException if the file or its parent directory cannot be created or opened
     */
    private BufferedWriter openWriter(String fileName, boolean append) throws IOException {
        Path path = Path.of(fileName);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        if (append) {
            return Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        return Files.newBufferedWriter(
                path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Overrides the configured logging severity for all channels.
     *
     * <p>When set, this threshold is used instead of each channel's configured severity. Passing {@code null} disables
     * the override.
     *
     * @param severity the global logging threshold, or {@code null} to disable the override
     */
    public void overwriteSeverity(LoggingSeverity severity) {
        this.overwriteSeverity = severity;
    }

    /** Logs a message without echoing it to the console. */
    public void log(String message, LoggingCategoryType categoryType, LoggingSeverity severity) {
        log(message, categoryType, severity, false);
    }

    /**
     * Logs a message to every channel associated with the given category whose configured severity allows it.
     * Optionally echoes the original message to the console.
     */
    public void log(
            String message, LoggingCategoryType categoryType, LoggingSeverity severity, boolean alsoPrintToConsole) {
        LoggingCategory category = logging.categories().get(categoryType);
        if (category == null) {
            return;
        }

        for (LoggingChannel channel : category.channels()) {
            /**
             * Use the global severity threshold if one has been configured. it checks for which severity is stricter,
             * the global threshold or the current one it picks the stricter one
             */
            LoggingSeverity threshold = channel.severity();
            if (overwriteSeverity != null) {
                threshold = channel.severity().level() <= overwriteSeverity.level()
                        ? channel.severity()
                        : overwriteSeverity;
            }

            if (severity.level() <= threshold.level()) {
                write(channel, message, categoryType, severity, alsoPrintToConsole);
            }
        }
    }

    /**
     * Formats and writes a single log entry to the specified logging channel. ANSI escape sequences are removed before
     * writing to the log file, while console output retains the original formatting.
     */
    private void write(
            LoggingChannel channel,
            String message,
            LoggingCategoryType categoryType,
            LoggingSeverity severity,
            boolean alsoPrintToConsole) {
        BufferedWriter writer = writers.get(channel.channelName());
        if (writer == null) {
            return;
        }
        try {
            StringBuilder line = new StringBuilder();
            if (channel.printTime()) {
                line.append('[').append(LocalDateTime.now().format(LOG_TIME)).append("] ");
            }
            if (channel.printCategory()) {
                line.append('[').append(categoryType).append("] ");
            }
            if (channel.printSeverity()) {
                line.append('[').append(severity).append("] ");
            }

            // Strip ANSI escape sequences before writing to the log file.
            String cleanedMessage = ANSI_PATTERN.matcher(message).replaceAll("");
            line.append(cleanedMessage);
            writer.write(line.toString());
            writer.newLine();
            writer.flush();

            rotateFileIfRequired(channel);

            // Optionally echo the original (ANSI-colored) message to the console.
            if (alsoPrintToConsole) {
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
     * Builds and logs a Tracelog message by prepending the qName, ID of a given query, and the log type [TRACE] . Also
     * adds colour definition
     *
     * @param message the log message
     * @param query the query that is to be logged
     */
    public void logTrace(String message, DNSPacket query) {
        String loggedMessage =
                PINK + "[TRACE] query: " + query.getQuestionSection(0).getNameString() + " id: " + query.getIdString()
                        + " type: " + query.getQuestionSection(0).getQtype() + ": " + message + RESET;
        log(loggedMessage, LoggingCategoryType.TRACE, LoggingSeverity.INFO);
    }

    /**
     * Checks whether the current log file for a channel exceeds its configured maximum size and rotates it if needed.
     *
     * <p>If the channel has no file name or no configured file size, no rotation is performed. Rotation only happens
     * after a log write, so the file may briefly exceed the configured size by roughly one log entry.
     *
     * @param channel the logging channel whose file should be checked
     * @throws IOException if the file size cannot be checked or rotation fails
     */
    private void rotateFileIfRequired(LoggingChannel channel) throws IOException {
        if (channel.fileName() == null || channel.fileName().isBlank()) {
            return;
        }
        if (channel.fileSize() == null) {
            return;
        }

        Path currentLog = Path.of(channel.fileName());
        if (!Files.exists(currentLog)) {
            return;
        }
        long maxBytes = channel.fileSize();
        if (maxBytes <= 0) {
            return;
        }
        long actualBytes = Files.size(currentLog);
        if (actualBytes <= maxBytes) {
            return;
        }
        rotateLogFile(channel);
    }

    /**
     * Rotates the log file for a channel according to its configured {@code versions} value.
     *
     * <p>The current writer is closed before rotating and a fresh writer is opened afterwards. The supported version
     * semantics are:
     *
     * <ul>
     *   <li>{@code null}: do not rotate; reopen the current file and continue appending
     *   <li>{@code -1}: unlimited versions; move the current file to the next free numbered suffix
     *   <li>{@code 0}: keep no old versions; delete the current file and start fresh
     *   <li>{@code N > 0}: keep {@code N} old numbered versions
     * </ul>
     *
     * @param channel the channel whose file should be rotated
     * @throws IOException if closing, moving, deleting, or reopening the log file fails
     */
    private void rotateLogFile(LoggingChannel channel) throws IOException {
        String channelName = channel.channelName();
        String fileName = channel.fileName();

        BufferedWriter writer = writers.remove(channelName);
        if (writer != null) {
            writer.close();
        }

        Path currentLog = Path.of(fileName);

        /*
         * If versions is missing, we do not rotate.
         *
         * This keeps the semantic difference:
         *   file "x.log" size 200k;
         *     -> size configured, but no rotation history configured
         *
         *   file "x.log" versions 0 size 200k;
         *     -> rotate/truncate at 200k, keep no old copies
         */
        if (channel.fileVersions() == null) {
            writers.put(channelName, openWriter(fileName, true));
            return;
        }
        int versions = channel.fileVersions();
        if (versions < 0) {
            /*
             * -1 can mean "versions unlimited".
             * For unlimited, find the next free suffix and move current log there.
             */
            rotateUnlimited(currentLog);
        } else if (versions == 0) {
            /*
             * Keep no old files.
             * Delete/truncate the current log and start fresh.
             */
            Files.deleteIfExists(currentLog);
        } else {
            /*
             * Keep N old files:
             *
             * queries.log.2 -> queries.log.3
             * queries.log.1 -> queries.log.2
             * queries.log.0 -> queries.log.1
             * queries.log   -> queries.log.0
             */
            rotateNumbered(currentLog, versions);
        }
        writers.put(channelName, openWriter(fileName, false));
    }

    /**
     * Rotates a log file while keeping a fixed number of old versions.
     *
     * <p>For example, with {@code versions = 3}, files are rotated like this:
     *
     * <pre>
     * log.1 -> log.2
     * log.0 -> log.1
     * log   -> log.0
     * </pre>
     *
     * <p>The oldest file beyond the configured version count is deleted.
     *
     * @param currentLog the active log file
     * @param versions the number of old rotated versions to keep
     * @throws IOException if deleting or moving files fails
     */
    private void rotateNumbered(Path currentLog, int versions) throws IOException {
        Path oldest = Path.of(currentLog.toString() + "." + (versions - 1));
        Files.deleteIfExists(oldest);
        for (int i = versions - 2; i >= 0; i--) {
            Path source = Path.of(currentLog + "." + i);
            Path target = Path.of(currentLog + "." + (i + 1));
            if (Files.exists(source)) {
                Files.move(source, target);
            }
        }
        if (Files.exists(currentLog)) {
            Files.move(currentLog, Path.of(currentLog + ".0"));
        }
    }

    /**
     * Rotates a log file using unlimited numbered versions.
     *
     * <p>The current log file is moved to the first available suffix, for example {@code log.0}, {@code log.1},
     * {@code log.2}, and so on. No old rotated files are deleted.
     *
     * @param currentLog the active log file
     * @throws IOException if moving the current file fails
     */
    private void rotateUnlimited(Path currentLog) throws IOException {
        int i = 0;
        while (Files.exists(Path.of(currentLog.toString() + "." + i))) {
            i++;
        }
        if (Files.exists(currentLog)) {
            Files.move(currentLog, Path.of(currentLog + "." + i));
        }
    }
}
