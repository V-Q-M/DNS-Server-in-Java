package io.gitlab.teamproject2026.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.gitlab.teamproject2026.config.interpreter.ConfLexer;
import io.gitlab.teamproject2026.config.interpreter.ConfToken;
import io.gitlab.teamproject2026.config.interpreter.ConfTokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConfLexerTest {
    @Test
    void testSingleCharToken() {
        String input = "{{;}};{{";
        List<ConfToken> expectedTokens = new ArrayList<>();

        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));
        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));
        expectedTokens.add(new ConfToken(ConfTokenType.EOF, ""));

        ConfLexer testLexer = new ConfLexer(input);
        for (ConfToken expectedTok : expectedTokens) {
            ConfToken ourTok = testLexer.nextToken();
            // Test type
            assertEquals(expectedTok.type(), ourTok.type());
            // Test literal
            assertEquals(expectedTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testAtomTokens() {
        String input = """
        options {
            directory "zones";
            recursion no;
        };

        zone example.com {
            type primary;
            file db.example.com;
        };
        """;

        List<ConfToken> expectedTokens = new ArrayList<>();

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "options"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "directory"));
        expectedTokens.add(new ConfToken(ConfTokenType.STRING, "zones"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "recursion"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "no"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "zone"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "example.com"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "type"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "primary"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "file"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "db.example.com"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));
        expectedTokens.add(new ConfToken(ConfTokenType.EOF, ""));

        ConfLexer testLexer = new ConfLexer(input);
        for (ConfToken expectedTok : expectedTokens) {
            ConfToken ourTok = testLexer.nextToken();
            assertEquals(expectedTok.type(), ourTok.type());
            assertEquals(expectedTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testForwardersAndResolverTokens() {
        String input = """
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
        """;

        List<ConfToken> expectedTokens = new ArrayList<>();

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "options"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "forwarders"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "8.8.8.8"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "1.1.1.1"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));
        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "resolver"));
        expectedTokens.add(new ConfToken(ConfTokenType.LBRACE, "{"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "timeout"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "2000"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "max-retries"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "2"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "retry-delay"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "100"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "strategy"));
        expectedTokens.add(new ConfToken(ConfTokenType.ATOM, "failover"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.RBRACE, "}"));
        expectedTokens.add(new ConfToken(ConfTokenType.SEMICOLON, ";"));

        expectedTokens.add(new ConfToken(ConfTokenType.EOF, ""));

        ConfLexer testLexer = new ConfLexer(input);

        for (ConfToken expectedTok : expectedTokens) {
            ConfToken ourTok = testLexer.nextToken();
            assertEquals(expectedTok.type(), ourTok.type());
            assertEquals(expectedTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testQuotedStrings() {
        String input = """
        options {
            directory "/var/named/my zones";
        };
        """;

        ConfLexer lexer = new ConfLexer(input);

        assertEquals("options", lexer.nextToken().literal());
        lexer.nextToken(); // {

        assertEquals("directory", lexer.nextToken().literal());

        ConfToken path = lexer.nextToken();
        assertEquals(ConfTokenType.STRING, path.type());
        assertEquals("/var/named/my zones", path.literal());
    }

    @Test
    void testCommentsIgnored() {
        String input = """
        # top level comment

        options {
            directory "zones"; # inline comment
        };
        """;

        List<ConfToken> expectedTokens = List.of(
                new ConfToken(ConfTokenType.ATOM, "options"),
                new ConfToken(ConfTokenType.LBRACE, "{"),
                new ConfToken(ConfTokenType.ATOM, "directory"),
                new ConfToken(ConfTokenType.STRING, "zones"),
                new ConfToken(ConfTokenType.SEMICOLON, ";"),
                new ConfToken(ConfTokenType.RBRACE, "}"),
                new ConfToken(ConfTokenType.SEMICOLON, ";"),
                new ConfToken(ConfTokenType.EOF, ""));

        ConfLexer lexer = new ConfLexer(input);

        for (ConfToken expected : expectedTokens) {
            ConfToken actual = lexer.nextToken();

            assertEquals(expected.type(), actual.type());
            assertEquals(expected.literal(), actual.literal());
        }
    }
}
