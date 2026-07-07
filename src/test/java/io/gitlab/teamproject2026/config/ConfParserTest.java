package io.gitlab.teamproject2026.config;

import static org.junit.jupiter.api.Assertions.*;

import io.gitlab.teamproject2026.config.interpreter.ConfNode;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConfParserTest {

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

    @Nested
    class ValidParsing {

        @Test
        void parsesSimpleNamedConfTree() {
            ConfParser parser = new ConfParser("""
                options {
                    directory "zones";

                    listen-on port 2222 {127.0.0.1; };
                    listen-on-v6 { none; };

                    recursion no;
                };

                zone "example.com" {
                    type primary;
                    file "db.example.com";
                };
                """);

            assertFalse(parser.hasParserError());

            List<ConfNode> roots = parser.getRootNodes();
            assertEquals(2, roots.size());

            ConfNode options = roots.get(0);
            assertEquals("options", options.name());
            assertEquals(List.of(), options.args());
            assertEquals(4, options.children().size());

            ConfNode directory = options.children().get(0);
            assertEquals("directory", directory.name());
            assertEquals(List.of("zones"), directory.args());
            assertEquals(List.of(), directory.children());

            ConfNode listenOn = options.children().get(1);
            assertEquals("listen-on", listenOn.name());
            assertEquals(List.of("port", "2222"), listenOn.args());
            assertEquals(1, listenOn.children().size());

            ConfNode listenOnAddress = listenOn.children().get(0);
            assertEquals("127.0.0.1", listenOnAddress.name());

            ConfNode listenOnV6 = options.children().get(2);
            assertEquals("listen-on-v6", listenOnV6.name());
            assertEquals(1, listenOnV6.children().size());
            assertEquals("none", listenOnV6.children().getFirst().name());

            ConfNode recursion = options.children().get(3);
            assertEquals("recursion", recursion.name());
            assertEquals(List.of("no"), recursion.args());

            ConfNode zone = roots.get(1);
            assertEquals("zone", zone.name());
            assertEquals(List.of("example.com"), zone.args());
            assertEquals(2, zone.children().size());

            assertEquals("type", zone.children().get(0).name());
            assertEquals(List.of("primary"), zone.children().get(0).args());

            assertEquals("file", zone.children().get(1).name());
            assertEquals(List.of("db.example.com"), zone.children().get(1).args());
        }

        @Test
        void parsesForwardersAndResolverBlock() {
            ConfParser parser = new ConfParser("""
                options {
                    forwarders {
                        8.8.8.8;
                        1.1.1.1;
                    };

                    resolver {
                        timeout 2000;
                        max-retries 2;
                        retry-delay 100;
                        strategy failover;
                    };
                };
                """);

            assertFalse(parser.hasParserError());

            ConfNode options = parser.getRootNodes().getFirst();
            assertEquals("options", options.name());
            assertEquals(2, options.children().size());

            ConfNode forwarders = options.children().get(0);
            assertEquals("forwarders", forwarders.name());
            assertEquals(2, forwarders.children().size());
            assertEquals("8.8.8.8", forwarders.children().get(0).name());
            assertEquals("1.1.1.1", forwarders.children().get(1).name());

            ConfNode resolver = options.children().get(1);
            assertEquals("resolver", resolver.name());
            assertEquals(4, resolver.children().size());

            assertEquals("timeout", resolver.children().get(0).name());
            assertEquals(List.of("2000"), resolver.children().get(0).args());

            assertEquals("max-retries", resolver.children().get(1).name());
            assertEquals(List.of("2"), resolver.children().get(1).args());

            assertEquals("retry-delay", resolver.children().get(2).name());
            assertEquals(List.of("100"), resolver.children().get(2).args());

            assertEquals("strategy", resolver.children().get(3).name());
            assertEquals(List.of("failover"), resolver.children().get(3).args());
        }

        @Test
        void parsesQuotedStringsAsArguments() {
            ConfParser parser = new ConfParser("""
                options {
                    directory "/var/named/zones";
                };
                """);

            assertFalse(parser.hasParserError());

            ConfNode directory = parser.getRootNodes().getFirst().children().getFirst();

            assertEquals("directory", directory.name());
            assertEquals(List.of("/var/named/zones"), directory.args());
        }

        @Test
        void emptyInputProducesNoNodes() {
            ConfParser parser = new ConfParser("");

            assertFalse(parser.hasParserError());
            assertEquals(List.of(), parser.getRootNodes());
        }
    }

    @Nested
    class InvalidSyntax {

        @Test
        void missingSemicolonFails() {
            String err = captureStderr(() -> {
                ConfParser parser = new ConfParser("""
                    options {
                        directory "zones"
                    };
                    """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Expected Argument"));
        }

        @Test
        void missingClosingBraceFails() {
            String err = captureStderr(() -> {
                ConfParser parser = new ConfParser("""
                    options {
                        directory "zones";
                    """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Expected closing brace"));
        }

        @Test
        void missingSemicolonAfterBlockFails() {
            String err = captureStderr(() -> {
                ConfParser parser = new ConfParser("""
                    options {
                        directory "zones";
                    }
                    """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Expected semicolon after block"));
        }

        @Test
        void invalidStatementNameFails() {
            String err = captureStderr(() -> {
                ConfParser parser = new ConfParser("""
                    {
                        directory "zones";
                    };
                    """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Expected statement name"));
        }

        @Test
        void unexpectedTokenInArgumentsFails() {
            String err = captureStderr(() -> {
                ConfParser parser = new ConfParser("""
                    options {
                        directory { "zones"; };
                    };
                    """);

                assertTrue(parser.hasParserError());
            });

            assertTrue(err.contains("Expected statement name"));
        }
    }
}
