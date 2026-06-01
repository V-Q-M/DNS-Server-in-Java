package io.gitlab.teamproject2026.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            IO.println(expectedTok.literal() + ":" + ourTok.literal());
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
            IO.println(expectedTok.literal() + ":" + ourTok.literal());
            assertEquals(expectedTok.type(), ourTok.type());
            assertEquals(expectedTok.literal(), ourTok.literal());
        }
    }


}
