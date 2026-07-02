package io.gitlab.teamproject2026.logging;

import static io.gitlab.teamproject2026.AnsiColors.*;

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
            try {
                Path path = Path.of(channel.fileName());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                writers.put(
                        channel.channelName(),
                        Files.newBufferedWriter(
                                path,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND));
            } catch (IOException e) {
                System.err.println("Failed to open log file: " + channel.fileName());
            }
        }
    }


    private BufferedWriter openWriter(String fileName, boolean append) throws IOException {
        Path path = Path.of(fileName);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        if (append) {
            return Files.newBufferedWriter( path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        return Files.newBufferedWriter( path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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

        // Use the global severity override if one has been configured.
        LoggingSeverity effectiveSeverity = overwriteSeverity != null ? overwriteSeverity : severity;

        for (LoggingChannel channel : category.channels()) {
            if (effectiveSeverity.level() <= channel.severity().level()) {
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

    private void rotateFileIfRequired(LoggingChannel channel) throws IOException {
        if (channel.fileName() == null || channel.fileName().isBlank()){
            return;
        }
        if (channel.fileSize() == null){
            return;
        }

        Path currentLog = Path.of(channel.fileName());
        if(!Files.exists(currentLog)){
            return;
        }
        long maxBytes = channel.fileSize();
        if (maxBytes <= 0){
            return;
        }
        long actualBytes = Files.size(currentLog);
        if (actualBytes <= maxBytes){
            return;
        }
        rotateLogFile(channel);
    }

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

    private void rotateNumbered(Path currentLog, int versions) throws IOException {
        Path oldest = Path.of(currentLog.toString() + "." + (versions - 1));
        Files.deleteIfExists(oldest);
        for (int i = versions - 2; i >= 0; i--) {
            Path source = Path.of(currentLog.toString() + "." + i);
            Path target = Path.of(currentLog.toString() + "." + (i + 1));
            if (Files.exists(source)) {
                Files.move(source, target);
            }
        }
        if (Files.exists(currentLog)) {
            Files.move(currentLog, Path.of(currentLog.toString() + ".0"));
        }
    }

    private void rotateUnlimited(Path currentLog) throws IOException {
        int i = 0;
        while (Files.exists(Path.of(currentLog.toString() + "." + i))) {
            i++;
        }
        if (Files.exists(currentLog)) {
            Files.move(currentLog, Path.of(currentLog.toString() + "." + i));
        }
    }
}
