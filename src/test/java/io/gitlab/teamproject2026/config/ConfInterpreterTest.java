package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.config.interpreter.ConfInterpreter;
import io.gitlab.teamproject2026.config.interpreter.ConfNode;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.config.zones.ZoneType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfInterpreterTest {

    @Test
    void testLoadSimpleConfig() {
        ConfParser parser = new ConfParser("""
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

        assertFalse(parser.hasParserError());

        Config config = ConfInterpreter.fromAst(parser.getRootNodes());

        assertTrue(config.options() != null);
        assertEquals(1, config.zones().size());

        OptionSection options = config.options();
        assertEquals("zones", options.dir());
        assertEquals(2222, options.port());
        assertEquals("127.0.0.1", options.ipAddress());
        assertFalse(options.recursionDesired());

        ZoneSection zone = config.zones().get(0);
        assertEquals("example.com", zone.name());
        assertEquals("db.example.com", zone.file());
        assertEquals(ZoneType.PRIMARY, zone.type());
    }

    @Test
    void testDefaultOptions() {
        ConfParser parser = new ConfParser("""
            options {
            };
            """);

        assertFalse(parser.hasParserError());

        Config config = ConfInterpreter.fromAst(parser.getRootNodes());
        OptionSection options = config.options();

        assertEquals("", options.dir());
        assertEquals(4242, options.port());
        assertEquals("0.0.0.0", options.ipAddress());
        assertFalse(options.recursionDesired());
    }

    @Test
    void testRecursionYes() {
        ConfParser parser = new ConfParser("""
            options {
                recursion yes;
            };
            """);

        Config config = ConfInterpreter.fromAst(parser.getRootNodes());

        assertTrue(config.options().recursionDesired());
    }

    @Test
    void testZoneMissingNameThrows() {
        ConfNode zone = new ConfNode("zone", java.util.List.of(), java.util.List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfInterpreter.parseZoneConfig(zone)
        );
    }

    @Test
    void testZoneMissingFileThrows() {
        ConfParser parser = new ConfParser("""
            zone "example.com" {
                type primary;
            };
            """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfInterpreter.fromAst(parser.getRootNodes())
        );
    }

    @Test
    void testZoneMissingTypeThrows() {
        ConfParser parser = new ConfParser("""
            zone "example.com" {
                file "db.example.com";
            };
            """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfInterpreter.fromAst(parser.getRootNodes())
        );
    }

    @Test
    void testListenOnMissingAddressThrows() {
        ConfParser parser = new ConfParser("""
            options {
                listen-on port 2222 { };
            };
            """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfInterpreter.fromAst(parser.getRootNodes())
        );
    }
}