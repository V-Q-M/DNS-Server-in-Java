package io.gitlab.teamproject2026.zones;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

public class ZoneLexerTest {
    @Test
    void testSingleCharToken() {
        String input = "( (())  @";

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, '('));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, '('));
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, '('));
        testTokens.add(new ZoneToken(ZoneTokenType.RPAREN, ')'));
        testTokens.add(new ZoneToken(ZoneTokenType.RPAREN, ')'));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, '@'));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testMultiCharToken() {
        String input = "$ORIGIN testing.com. $TTL 3600";

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ORIGIN, "$ORIGIN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "testing.com."));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TTL, "$TTL"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "3600"));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testIPAddressToken() {
        String input = "@       IN A      192.0.2.1\nipv6    IN AAAA   2001:db8::1";

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.A_RECORD, "A"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.IPV4_ADDRESS, "192.0.2.1"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "ipv6"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.AAAA_RECORD, "AAAA"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.IPV6_ADDRESS, "2001:db8::1"));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testSOARecordToken() {
        String input = """
                @ IN SOA ns1.testing.com. admin.testing.com. (
                2026051201
                3600
                1800
                1209600
                3600
                )
                """;
        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.SOA_RECORD, "SOA"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "ns1.testing.com."));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "admin.testing.com."));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, "("));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "2026051201"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "3600"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "1800"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "1209600"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "3600"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.RPAREN, ")"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testPTRRecordToken() {
       String input = "1.2.0.192.in-addr.arpa. IN PTR host1.example.com.";

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "1.2.0.192.in-addr.arpa."));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.PTR_RECORD, "PTR"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "host1.example.com."));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }


    @Test
    void testRecordTokens() {
        String input = """
                @       IN NS     ns1.testing.com.
                www     IN A      192.0.2.2
                ipv6    IN AAAA   2001:db8::1
                ns2     IN A      192.0.2.54
                alias   IN CNAME  www.testing.com.
                @       IN MX     10 mail1.testing.com.
                """;
        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.NS_RECORD, "NS"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "ns1.testing.com."));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "www"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.A_RECORD, "A"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.IPV4_ADDRESS, "192.0.2.2"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "ipv6"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.AAAA_RECORD, "AAAA"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.IPV6_ADDRESS, "2001:db8::1"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "ns2"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.A_RECORD, "A"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.IPV4_ADDRESS, "192.0.2.54"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "alias"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CNAME_RECORD, "CNAME"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "www.testing.com."));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.MX_RECORD, "MX"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.NUMBER, "10"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "mail1.testing.com."));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testTextRecord() {
        String input = """
                @       IN TXT    "hello world"
                split   IN TXT    "one" "two" "three"
                
                multitxt IN TXT (
                    "this is "
                    "multiple txt chunks"
                )
                """;

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "hello world"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "split"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "one"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "two"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "three"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "multitxt"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, "("));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "this is "));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "multiple txt chunks"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.RPAREN, ")"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }

    @Test
    void testEscapedTextRecord() {
        String input = """
                @       IN TXT    "hello \\"world\\""
                split   IN TXT    "one" "two" "three"
                
                multitxt IN TXT (
                    "this is "
                    "multiple \\"\\"txt\\" chunks"
                )
                """;

        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "@"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "hello \\\"world\\\""));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "split"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "one"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "two"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "three"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "multitxt"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.CLASS, "IN"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT_RECORD, "TXT"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.LPAREN, "("));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "this is "));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.TXT, "multiple \\\"\\\"txt\\\" chunks"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));
        testTokens.add(new ZoneToken(ZoneTokenType.RPAREN, ")"));
        testTokens.add(new ZoneToken(ZoneTokenType.NEWLINE, "\n"));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();

            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }
    @Test
    void testInclude() {
        String input = "$INCLUDE /path/example example.com.";
        ArrayList<ZoneToken> testTokens = new ArrayList<>();
        testTokens.add(new ZoneToken(ZoneTokenType.INCLUDE, "$INCLUDE"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "/path/example"));
        testTokens.add(new ZoneToken(ZoneTokenType.SPACE, ' '));
        testTokens.add(new ZoneToken(ZoneTokenType.ATOM, "example.com."));

        ZoneLexer testLexer = new ZoneLexer(input);
        for (ZoneToken testTok : testTokens) {
            ZoneToken ourTok = testLexer.nextToken();
            //IO.println(ourTok.getLiteral() + ":" + ourTok.getType());
            // Test type
            assertEquals(testTok.type(), ourTok.type());
            // Test literal
            assertEquals(testTok.literal(), ourTok.literal());
        }
    }
}
