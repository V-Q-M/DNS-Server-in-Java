package io.gitlab.teamproject2026.config;

import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.config.interpreter.ConfInterpreter;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.config.zones.ZoneType;
import io.gitlab.teamproject2026.resolver.ForwardMode;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConfInterpreterTest {

    private String captureStderr(Runnable runnable) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try {
            runnable.run();
        } finally {
            System.setErr(originalErr);
        }

        return errContent.toString();
    }

    private Config parseValidConfig(String input) {
        ConfParser parser = new ConfParser(input);
        assertFalse(parser.hasParserError());

        ConfInterpreter interpreter = new ConfInterpreter();
        Config config = interpreter.fromAst(parser.getRootNodes());

        assertFalse(interpreter.hasInterpreterError());
        assertNotNull(config);

        return config;
    }

    private ConfInterpreter interpretInvalidConfig(String input) {
        ConfParser parser = new ConfParser(input);
        assertFalse(parser.hasParserError());

        ConfInterpreter interpreter = new ConfInterpreter();
        Config config = interpreter.fromAst(parser.getRootNodes());

        assertTrue(interpreter.hasInterpreterError());
        assertNull(config);

        return interpreter;
    }

    @Nested
    class ValidConfigParsing {

        @Test
        void parsesSimpleConfig() {
            Config config = parseValidConfig("""
                    options {
                        directory "zones";
                        listen-on port 2222 { 127.0.0.1; };
                        recursion no;
                    };

                    zone "example.com" {
                        type primary;
                        file "db.example.com";
                    };
                    """);

            assertNotNull(config.options());
            assertEquals(1, config.zones().size());

            OptionSection options = config.options();
            assertEquals("zones", options.dir());
            assertEquals(2222, options.port());
            assertEquals("127.0.0.1", options.ipv4Address());
            assertEquals("none", options.ipv6Address());
            assertFalse(options.recursionAllowed());

            ZoneSection zone = config.zones().getFirst();
            assertEquals("example.com", zone.name());
            assertEquals("db.example.com", zone.file());
            assertEquals(ZoneType.PRIMARY, zone.type());
        }

        @Test
        void parsesMultipleZones() {
            Config config = parseValidConfig("""
                    options {
                    };

                    zone "example.com" {
                        type primary;
                        file "db.example.com";
                    };

                    zone "example.org" {
                        type primary;
                        file "db.example.org";
                    };
                    """);

            assertEquals(2, config.zones().size());

            assertEquals("example.com", config.zones().get(0).name());
            assertEquals("db.example.com", config.zones().get(0).file());
            assertEquals(ZoneType.PRIMARY, config.zones().get(0).type());

            assertEquals("example.org", config.zones().get(1).name());
            assertEquals("db.example.org", config.zones().get(1).file());
            assertEquals(ZoneType.PRIMARY, config.zones().get(1).type());
        }
    }

    @Nested
    class DefaultOptions {

        @Test
        void usesDefaultOptions() {
            Config config = parseValidConfig("""
                    options {
                    };
                    """);

            OptionSection options = config.options();

            assertEquals("", options.dir());
            assertEquals(4242, options.port());
            assertEquals("0.0.0.0", options.ipv4Address());
            assertEquals("none", options.ipv6Address());
            assertFalse(options.recursionAllowed());
            assertEquals(null, options.forwardMode());
            assertEquals(List.of("1.1.1.1", "8.8.8.8"), options.forwarders());

            ResolverSection resolver = options.resolverSection();
            assertEquals(2000, resolver.timeout());
            assertEquals(2, resolver.maxRetries());
            assertEquals(100, resolver.retryDelay());
            assertEquals(StubResolverStrategy.FAILOVER, resolver.strategy());
        }

        @Test
        void recursionYesSetsRecursionDesired() {
            Config config = parseValidConfig("""
                    options {
                        recursion yes;
                    };
                    """);

            assertTrue(config.options().recursionAllowed());
        }

        @Test
        void forwardModeCanBeConfigured() {
            Config config = parseValidConfig("""
                    options {
                        forward only;
                    };
                    """);

            assertEquals(ForwardMode.FORWARD_ONLY, config.options().forwardMode());
        }
    }

    @Nested
    class ForwarderParsing {

        @Test
        void parsesForwarders() {
            Config config = parseValidConfig("""
                    options {
                        forwarders {
                            9.9.9.9;
                            1.1.1.1;
                            8.8.8.8;
                        };
                    };
                    """);

            assertEquals(
                    List.of("9.9.9.9", "1.1.1.1", "8.8.8.8"), config.options().forwarders());
        }

        @Test
        void emptyForwardersFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        forwarders {
                        };
                    };
                    """));

            assertTrue(err.contains("options.forwarders requires at least one server"));
        }
    }

    @Nested
    class ResolverParsing {

        @Test
        void parsesResolverSection() {
            Config config = parseValidConfig("""
                    options {
                        resolver {
                            timeout 5000;
                            max-retries 4;
                            retry-delay 250;
                            strategy round-robin;
                        };
                    };
                    """);

            ResolverSection resolver = config.options().resolverSection();

            assertEquals(5000, resolver.timeout());
            assertEquals(4, resolver.maxRetries());
            assertEquals(250, resolver.retryDelay());
            assertEquals(StubResolverStrategy.ROUND_ROBIN, resolver.strategy());
        }

        @Test
        void invalidResolverTimeoutFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                            timeout abc;
                        };
                    };
                    """));

            assertTrue(err.contains("resolver.timeout must be an integer: abc"));
        }

        @Test
        void invalidResolverMaxRetriesFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                            max-retries abc;
                        };
                    };
                    """));

            assertTrue(err.contains("resolver.max-retries must be an integer: abc"));
        }

        @Test
        void invalidResolverRetryDelayFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                            retry-delay abc;
                        };
                    };
                    """));

            assertTrue(err.contains("resolver.retry-delay must be a long: abc"));
        }

        @Test
        void invalidResolverStrategyFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                            strategy nonsense;
                        };
                    };
                    """));

            assertTrue(err.contains("Invalid resolver.strategy: \"nonsense\""));
        }

        @Test
        void unknownResolverDirectiveFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                            unknown-setting 123;
                        };
                    };
                    """));

            assertTrue(err.contains("Unknown resolver directive: \"unknown-setting\""));
        }

        @Test
        void emptyResolverFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        resolver {
                        };
                    };
                    """));

            assertTrue(err.contains("options.resolver requires at least one entry"));
        }
    }

    @Nested
    class OptionValidation {

        @Test
        void duplicateOptionsFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                    };

                    options {
                    };
                    """));

            assertTrue(err.contains("Only one options section allowed"));
        }

        @Test
        void unknownTopLevelSectionFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    unknown-section {
                    };
                    """));

            assertTrue(err.contains("Unknown top-level section: \"unknown-section\""));
        }

        @Test
        void unknownOptionsDirectiveFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        unknown-option yes;
                    };
                    """));

            assertTrue(err.contains("Unknown options directive: \"unknown-option\""));
        }

        @Test
        void directoryWithoutPathFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        directory;
                    };
                    """));

            assertTrue(err.contains("options.directory requires a path"));
        }

        @Test
        void listenOnMissingAddressFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        listen-on port 2222 { };
                    };
                    """));

            assertTrue(err.contains("options.listen-on requires an IP address or 'none'"));
        }

        @Test
        void invalidListenOnPortFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        listen-on port abc { 127.0.0.1; };
                    };
                    """));

            assertTrue(err.contains("options.listen-on port must be an integer: abc"));
        }

        @Test
        void recursionWithoutValueFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        recursion;
                    };
                    """));

            assertTrue(err.contains("options.recursion requires yes or no"));
        }

        @Test
        void forwardWithoutModeFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    options {
                        forward;
                    };
                    """));

            assertTrue(err.contains("forward requires one mode"));
        }
    }

    @Nested
    class ZoneParsing {

        @Test
        void parsesZoneConfig() {
            Config config = parseValidConfig("""
                    zone "example.com" {
                        type primary;
                        file "db.example.com";
                    };
                    """);

            assertEquals(1, config.zones().size());

            ZoneSection zone = config.zones().getFirst();
            assertEquals("example.com", zone.name());
            assertEquals("db.example.com", zone.file());
            assertEquals(ZoneType.PRIMARY, zone.type());
        }

        @Test
        void zoneMissingNameFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone {
                        type primary;
                        file "db.example.com";
                    };
                    """));

            assertTrue(err.contains("zone requires a name"));
        }

        @Test
        void zoneMissingFileFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone "example.com" {
                        type primary;
                    };
                    """));

            assertTrue(err.contains("zone example.com is missing file"));
        }

        @Test
        void zoneMissingTypeFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone "example.com" {
                        file "db.example.com";
                    };
                    """));

            assertTrue(err.contains("zone example.com is missing type"));
        }

        @Test
        void zoneFileWithoutPathFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone "example.com" {
                        type primary;
                        file;
                    };
                    """));

            assertTrue(err.contains("zone example.com file requires a path"));
        }

        @Test
        void zoneTypeWithoutValueFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone "example.com" {
                        type;
                        file "db.example.com";
                    };
                    """));

            assertTrue(err.contains("zone example.com type requires a value"));
        }

        @Test
        void invalidZoneTypeFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    zone "example.com" {
                        type nonsense;
                        file "db.example.com";
                    };
                    """));

            assertTrue(err.contains("Invalid zone type: nonsense"));
        }
    }

    @Nested
    class LoggingParsing {

        @Test
        void parsesLoggingSection() {
            Config config = parseValidConfig("""
                    logging {
                        channel default_log {
                            file "log/named.log";
                            severity info;
                        };

                        category default { default_log; };
                        category queries { default_log; };
                    };
                    """);

            assertNotNull(config.logging());

            assertEquals(1, config.logging().channels().size());

            var channel = config.logging().channels().getFirst();
            assertEquals("default_log", channel.channelName());
            assertEquals("log/named.log", channel.fileName());
            assertEquals(LoggingSeverity.INFO, channel.severity());

            assertEquals(2, config.logging().categories().size());

            assertTrue(config.logging().categories().containsKey(LoggingCategoryType.DEFAULT));
            assertTrue(config.logging().categories().containsKey(LoggingCategoryType.QUERIES));

            assertEquals(
                    channel,
                    config.logging()
                            .categories()
                            .get(LoggingCategoryType.DEFAULT)
                            .channels()
                            .getFirst());
        }

        @Test
        void invalidLoggingSeverityFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                        channel default_log {
                            file "log/named.log";
                            severity nonsense;
                        };
                    };
                    """));

            assertTrue(err.contains("Invalid logging severity: nonsense"));
        }

        @Test
        void unknownLoggingCategoryFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                        channel default_log {
                            file "log/named.log";
                        };

                        category made-up { default_log; };
                    };
                    """));

            assertTrue(err.contains("Unknown logging category: made-up"));
        }

        @Test
        void unknownLoggingChannelFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                        category default { does_not_exist; };
                    };
                    """));

            assertTrue(err.contains("Unknown logging channel: does_not_exist"));
        }

        @Test
        void duplicateLoggingSectionsFail() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                    };

                    logging {
                    };
                    """));

            assertTrue(err.contains("Only one logging section allowed"));
        }

        @Test
        void loggingChannelWithoutNameFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                        channel {
                            file "log/named.log";
                        };
                    };
                    """));

            assertTrue(err.contains("logging.channel requires a name"));
        }

        @Test
        void loggingCategoryWithoutNameFails() {
            String err = captureStderr(() -> interpretInvalidConfig("""
                    logging {
                        category {
                            default_log;
                        };
                    };
                    """));

            assertTrue(err.contains("logging.category requires a name"));
        }

        @Test
        void parsesMultipleLoggingChannels() {
            Config config = parseValidConfig("""
        logging {
            channel default_log {
                file "log/default.log";
                severity info;
            };

            channel query_log {
                file "log/query.log";
                severity warning;
            };

            category queries {
                default_log;
                query_log;
            };
        };
        """);

            assertEquals(2, config.logging().channels().size());

            var queries = config.logging().categories().get(LoggingCategoryType.QUERIES);

            assertEquals(2, queries.channels().size());

            assertEquals("default_log", queries.channels().get(0).channelName());
            assertEquals("query_log", queries.channels().get(1).channelName());
        }

        @Test
        void parsesHyphenatedCategoryNames() {
            Config config = parseValidConfig("""
        logging {
            channel default_log {
                file "log/named.log";
            };

            category query-errors {
                default_log;
            };
        };
        """);

            assertTrue(config.logging().categories().containsKey(LoggingCategoryType.QUERY_ERRORS));
        }

        @Test
        void parsesLoggingPrintFlags() {
            Config config = parseValidConfig("""
            logging {
                channel default_log {
                    file "log/named.log";
                    print-time yes;
                    print-category yes;
                    print-severity no;
                };
            };
            """);

            var channel = config.logging().channels().getFirst();

            assertTrue(channel.printTime());
            assertTrue(channel.printCategory());
            assertFalse(channel.printSeverity());
        }
    }
}
