package io.gitlab.teamproject2026.config.interpreter;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.*;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.config.zones.ZoneType;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interprets the configuration AST represented by {@link ConfNode}s and converts it into validated {@link Config}
 * objects. Responsible for applying defaults, validating directives, and reporting semantic configuration errors.
 */
public class ConfInterpreter {
    private boolean interpreterError = false;

    /**
     * Converts the parsed configuration AST into a validated {@link Config}.
     *
     * @param rootNodes top-level {@link ConfNode}s produced by the parser
     * @return the interpreted {@link Config}, or {@code null} if interpretation fails
     */
    public Config fromAst(List<ConfNode> rootNodes) {
        LoggingSection logging = null;
        OptionSection option = null;
        List<ZoneSection> zones = new ArrayList<>();

        for (ConfNode node : rootNodes) {
            String name = node.name().toLowerCase();
            switch (name) {
                case "logging" -> {
                    if (logging == null) {
                        logging = parseLoggingConfig(node);
                    } else {
                        error("Only one logging section allowed", node.lineNumber());
                    }
                }
                case "options" -> {
                    // Check first if option is already set
                    if (option == null) {
                        option = parseOptionConfig(node);
                    } else {
                        error("Only one options section allowed", node.lineNumber());
                    }
                }
                case "zone" -> {
                    ZoneSection zone = parseZoneConfig(node);
                    if (zone != null) {
                        zones.add(zone);
                    }
                }
                default -> error("Unknown top-level section: \"" + node.name() + "\"", node.lineNumber());
            }
        }

        return interpreterError ? null : new Config(logging, option, zones);
    }

    /**
     * Interprets the {@code logging} section of the configuration AST.
     *
     * <p>Parses all logging channels and categories, validates references between them, and constructs the resulting
     * {@link LoggingSection}. Categories may reference one or more previously defined channels.
     *
     * @param root the root {@link ConfNode} representing the logging section
     * @return the resulting {@link LoggingSection}
     */
    public LoggingSection parseLoggingConfig(ConfNode root) {
        // All logging channels defined in the configuration.
        List<LoggingChannel> channels = new ArrayList<>();
        Map<LoggingCategoryType, LoggingCategory> categories = new HashMap<>();

        // Lookup table so categories can reference channels by name.
        Map<String, LoggingChannel> channelLookup = new HashMap<>();

        for (ConfNode node : root.children()) {
            if (node.name().equalsIgnoreCase("channel")) {
                LoggingChannel channel = parseLoggingChannel(node);
                if (channel != null) {
                    channels.add(channel);
                    channelLookup.put(channel.channelName(), channel);
                }

            } else if (node.name().equalsIgnoreCase("category")) {
                if (node.args().isEmpty()) {
                    error("logging.category requires a name", node.lineNumber());
                    continue;
                }
                LoggingCategoryType type;
                try {
                    // Convert BIND syntax (e.g. "query-errors") into the enum name (QUERY_ERRORS).
                    type = LoggingCategoryType.valueOf(
                            node.args().getFirst().replace('-', '_').toUpperCase());
                } catch (IllegalArgumentException e) {
                    error("Unknown logging category: " + node.args().getFirst(), node.lineNumber());
                    continue;
                }
                List<LoggingChannel> categoryChannels = new ArrayList<>();
                for (ConfNode child : node.children()) {
                    LoggingChannel channel = channelLookup.get(child.name());
                    if (channel == null) {
                        error("Unknown logging channel: " + child.name(), child.lineNumber());
                        continue;
                    }
                    categoryChannels.add(channel);
                }
                categories.put(type, new LoggingCategory(type, categoryChannels));
            } else {
                error("Unknown logging directive: \"" + node.name() + "\"", node.lineNumber());
            }
        }

        return new LoggingSection(channels, categories);
    }

    /**
     * Interprets a single {@code channel} declaration from the logging section.
     *
     * <p>Reads the channel name, output file, logging severity, and formatting options. Unknown directives or invalid
     * values are reported as configuration errors.
     *
     * @param root the {@link ConfNode} representing the channel declaration
     * @return the resulting {@link LoggingChannel}, or {@code null} if the channel declaration is invalid
     */
    public LoggingChannel parseLoggingChannel(ConfNode root) {
        if (root.args().isEmpty()) {
            error("logging.channel requires a name", root.lineNumber());
            return null;
        }
        String channelName = root.args().getFirst();
        String fileName = "";
        Integer fileVersions = null; // null = not configured; -1 = unlimited
        Long fileSize = null; // bytes; null = not configured

        LoggingSeverity severity = LoggingSeverity.INFO;
        boolean printTime = false;
        boolean printCategory = false;
        boolean printSeverity = false;

        for (ConfNode node : root.children()) {
            switch (node.name().toLowerCase()) {
                case "file" -> {
                    ParsedFileLogging parsed = parseFileLoggingArgs(node);
                    if (parsed != null) {
                        fileName = parsed.fileName();
                        fileVersions = parsed.versions();
                        fileSize = parsed.sizeBytes();
                    }
                }
                case "severity" -> {
                    if (node.args().isEmpty()) {
                        error("logging.channel.severity requires a value", node.lineNumber());
                        continue;
                    }
                    try {
                        severity = LoggingSeverity.fromValue(node.args().getFirst());
                    } catch (IllegalArgumentException e) {
                        error("Invalid logging severity: " + node.args().getFirst(), node.lineNumber());
                    }
                }
                case "print-time" -> {
                    if (node.args().isEmpty()) {
                        error("logging.channel.print-time requires yes or no", node.lineNumber());
                        continue;
                    }

                    printTime = node.args().getFirst().equalsIgnoreCase("yes");
                }
                case "print-category" -> {
                    if (node.args().isEmpty()) {
                        error("logging.channel.print-category requires yes or no", node.lineNumber());
                        continue;
                    }

                    printCategory = node.args().getFirst().equalsIgnoreCase("yes");
                }
                case "print-severity" -> {
                    if (node.args().isEmpty()) {
                        error("logging.channel.print-severity requires yes or no", node.lineNumber());
                        continue;
                    }
                    printSeverity = node.args().getFirst().equalsIgnoreCase("yes");
                }
                default -> warning("Unknown logging channel directive: \"" + node.name() + "\"", node.lineNumber());
            }
        }

        return new LoggingChannel(
                channelName, fileName, fileVersions, fileSize, severity, printTime, printCategory, printSeverity);
    }

    private record ParsedFileLogging(String fileName, Integer versions, Long sizeBytes) {}

    private ParsedFileLogging parseFileLoggingArgs(ConfNode node) {
        List<String> args = node.args();
        if (args.isEmpty()) {
            error("logging.channel.file requires a path", node.lineNumber());
            return null;
        }
        String fileName = args.get(0);
        Integer versions = null;
        Long sizeBytes = null;
        int i = 1;
        while (i < args.size()) {
            String keyword = args.get(i).toLowerCase();
            switch (keyword) {
                case "versions" -> {
                    if (i + 1 >= args.size()) {
                        error("logging.channel.file versions requires a value", node.lineNumber());
                        return new ParsedFileLogging(fileName, versions, sizeBytes);
                    }
                    String value = args.get(i + 1);
                    if (value.equalsIgnoreCase("unlimited")) {
                        versions = -1; // sentinel for unlimited
                    } else {
                        try {
                            versions = Integer.parseInt(value);
                            if (versions < 0) {
                                error("logging.channel.file versions cannot be negative", node.lineNumber());
                            }
                        } catch (NumberFormatException e) {
                            error("Invalid logging.channel.file versions value: " + value, node.lineNumber());
                        }
                    }
                    i += 2;
                }
                case "size" -> {
                    if (i + 1 >= args.size()) {
                        error("logging.channel.file size requires a value", node.lineNumber());
                        return new ParsedFileLogging(fileName, versions, sizeBytes);
                    }
                    String value = args.get(i + 1);
                    try {
                        sizeBytes = parseSizeToBytes(value);
                    } catch (IllegalArgumentException e) {
                        error("Invalid logging.channel.file size value: " + value, node.lineNumber());
                    }
                    i += 2;
                }
                default -> {
                    warning("Unknown logging.channel.file option: \"" + args.get(i) + "\"", node.lineNumber());
                    i++;
                }
            }
        }
        return new ParsedFileLogging(fileName, versions, sizeBytes);
    }

    private long parseSizeToBytes(String value) {
        String trimmed = value.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty size");
        }
        long multiplier = 1;
        char last = trimmed.charAt(trimmed.length() - 1);
        String numberPart = trimmed;
        switch (last) {
            case 'k' -> {
                multiplier = 1_000L;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            case 'm' -> {
                multiplier = 1_000_000L;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            case 'g' -> {
                multiplier = 1_000_000_000L;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            default -> {
                if (!Character.isDigit(last)) {
                    throw new IllegalArgumentException("unknown size suffix: " + last);
                }
            }
        }
        try {
            long number = Long.parseLong(numberPart);
            if (number < 0) {
                throw new IllegalArgumentException("negative size");
            }
            return Math.multiplyExact(number, multiplier);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("invalid size: " + value, e);
        }
    }

    /**
     * Interprets the {@code options} section of the configuration AST.
     *
     * @return the resulting {@link OptionSection}
     */
    public OptionSection parseOptionConfig(ConfNode root) {
        // Default values
        String directoryPath = "";
        int port = 4242;
        String ipAddress = "0.0.0.0";
        String ipv6Address = "0.0.0.0";
        boolean recursionAllowed = false;
        ForwardMode forwardMode = null;
        List<String> forwarders = List.of("1.1.1.1", "8.8.8.8");

        int resolverTimeOut = 2000;
        int resolverMaxRetries = 2;
        long resolverRetryDelay = 100;
        StubResolverStrategy resolverStrategy = StubResolverStrategy.FAILOVER;

        for (ConfNode node : root.children()) {
            if (node.name().equalsIgnoreCase("directory")) {
                if (node.args().isEmpty()) {
                    error("options.directory requires a path", node.lineNumber());
                    continue;
                }
                directoryPath = node.args().getFirst();
            } else if (node.name().equalsIgnoreCase("listen-on")) {
                if (node.args().size() >= 2 && node.args().getFirst().equalsIgnoreCase("port")) {
                    port = parseInt(node.args().get(1), "options.listen-on port", node.lineNumber());
                }
                if (node.children().isEmpty()) {
                    error("options.listen-on requires an IP address or 'none'", node.lineNumber());
                    continue;
                }
                ipAddress = node.children().getFirst().name();
            } else if (node.name().equalsIgnoreCase("listen-on-v6")) {
                if (node.args().size() >= 2 && node.args().getFirst().equalsIgnoreCase("port")) {
                    port = parseInt(node.args().get(1), "options.listen-on port", node.lineNumber());
                }
                if (node.children().isEmpty()) {
                    error("options.listen-on-v6 requires an IP address or 'none'", node.lineNumber());
                    continue;
                }
                ipv6Address = node.children().getFirst().name();
            } else if (node.name().equalsIgnoreCase("recursion")) {
                if (node.args().isEmpty()) {
                    error("options.recursion requires yes or no", node.lineNumber());
                    continue;
                }
                recursionAllowed = node.args().getFirst().equalsIgnoreCase("yes");
            } else if (node.name().equalsIgnoreCase("forwarders")) {
                if (node.children().isEmpty()) {
                    error("options.forwarders requires at least one server", node.lineNumber());
                    continue;
                }
                forwarders = node.children().stream().map(ConfNode::name).toList();
            } else if (node.name().equalsIgnoreCase("forward")) {
                if (node.args().isEmpty()) {
                    error("forward requires one mode", node.lineNumber());
                    continue;
                }
                forwardMode = ForwardMode.fromValue(node.args().getFirst());
            } else if (node.name().equalsIgnoreCase("resolver")) {
                if (node.children().isEmpty()) {
                    error("options.resolver requires at least one entry", node.lineNumber());
                    continue;
                }
                for (ConfNode child : node.children()) {
                    if (child.name().equalsIgnoreCase("timeout")) {
                        if (child.args().isEmpty()) {
                            error("resolver.timeout requires a value", child.lineNumber());
                            continue;
                        }
                        resolverTimeOut = parseInt(child.args().getFirst(), "resolver.timeout", child.lineNumber());
                    } else if (child.name().equalsIgnoreCase("max-retries")) {
                        if (child.args().isEmpty()) {
                            error("resolver.max-retries requires a value", child.lineNumber());
                            continue;
                        }
                        resolverMaxRetries =
                                parseInt(child.args().getFirst(), "resolver.max-retries", child.lineNumber());
                    } else if (child.name().equalsIgnoreCase("retry-delay")) {
                        if (child.args().isEmpty()) {
                            error("resolver.retry-delay requires a value", child.lineNumber());
                            continue;
                        }
                        resolverRetryDelay =
                                parseLong(child.args().getFirst(), "resolver.retry-delay", child.lineNumber());
                    } else if (child.name().equalsIgnoreCase("strategy")) {
                        if (child.args().isEmpty()) {
                            error("resolver.strategy requires a value", child.lineNumber());
                            continue;
                        }
                        try {
                            resolverStrategy =
                                    StubResolverStrategy.fromValue(child.args().getFirst());
                        } catch (IllegalArgumentException e) {
                            error("Invalid resolver.strategy: \"" + child.args().getFirst() + "\"", child.lineNumber());
                        }
                    } else {
                        error("Unknown resolver directive: \"" + child.name() + "\"", child.lineNumber());
                    }
                }
            } else {
                error("Unknown options directive: \"" + node.name() + "\"", node.lineNumber());
            }
        }

        // Build the resolverSection first. It's syntactically part of options but logically its own thing
        ResolverSection resolverSection =
                new ResolverSection(resolverTimeOut, resolverMaxRetries, resolverRetryDelay, resolverStrategy);

        return new OptionSection(
                directoryPath, port, ipAddress, recursionAllowed, forwardMode, forwarders, resolverSection);
    }

    /**
     * Interprets a {@code zone} declaration from the configuration AST.
     *
     * @return the resulting {@link ZoneSection}, or {@code null} if validation fails
     */
    public ZoneSection parseZoneConfig(ConfNode root) {
        if (root.args().isEmpty()) {
            error("zone requires a name", root.lineNumber());
            return null;
        }
        String name = root.args().getFirst();
        String file = "";
        ZoneType type = null;

        for (ConfNode node : root.children()) {
            if (node.name().equalsIgnoreCase("file")) {
                if (node.args().isEmpty()) {
                    error("zone " + name + " file requires a path", node.lineNumber());
                    continue;
                }
                file = node.args().getFirst();
            } else if (node.name().equalsIgnoreCase("type")) {
                if (node.args().isEmpty()) {
                    error("zone " + name + " type requires a value", node.lineNumber());
                    continue;
                }
                try {
                    type = ZoneType.fromValue(node.args().getFirst().toLowerCase());
                } catch (IllegalArgumentException e) {
                    error("Invalid zone type: " + node.args().getFirst(), node.lineNumber());
                }
            }
        }

        if (file.isEmpty()) {
            error("zone " + name + " is missing file", root.lineNumber());
            return null;
        }

        if (type == null) {
            error("zone " + name + " is missing type", root.lineNumber());
            return null;
        }

        return new ZoneSection(name, file, type);
    }

    private int parseInt(String value, String field, int line) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            error(field + " must be an integer: " + value, line);
            return 0;
        }
    }

    private long parseLong(String value, String field, int line) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            error(field + " must be a long: " + value, line);
            return 0;
        }
    }

    /** Reports a fatal config interpreting error and marks the interpreter as failed. */
    private void error(String message, int lineNumber) {
        ConfigManager.logger.log(
                (RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " line " + RESET + YELLOW + lineNumber + RESET + RED
                        + ": " + message + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true);
        interpreterError = true;
    }

    /** Reports a non-fatal config warning. */
    private void warning(String message, int lineNumber) {
        ConfigManager.logger.log(
                (YELLOW + BOLD + "[CONFIG-WARNING]" + RESET + YELLOW + " line " + RESET + CYAN + lineNumber + RESET
                        + YELLOW + ": " + message + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.WARNING,
                true);
    }

    public boolean hasInterpreterError() {
        return this.interpreterError;
    }
}
