package io.gitlab.teamproject2026.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfLoaderTest {

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

        Config config = ConfLoader.fromAst(parser.getRootNodes());

        assertTrue(config.options() != null);
        assertEquals(1, config.zones().size());

        OptionConfig options = config.options();
        assertEquals("zones", options.dir());
        assertEquals(2222, options.port());
        assertEquals("127.0.0.1", options.ipAddress());
        assertFalse(options.recursionDesired());

        ZoneConfig zone = config.zones().get(0);
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

        Config config = ConfLoader.fromAst(parser.getRootNodes());
        OptionConfig options = config.options();

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

        Config config = ConfLoader.fromAst(parser.getRootNodes());

        assertTrue(config.options().recursionDesired());
    }

    @Test
    void testZoneMissingNameThrows() {
        ConfNode zone = new ConfNode("zone", java.util.List.of(), java.util.List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfLoader.parseZoneConfig(zone)
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
                () -> ConfLoader.fromAst(parser.getRootNodes())
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
                () -> ConfLoader.fromAst(parser.getRootNodes())
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
                () -> ConfLoader.fromAst(parser.getRootNodes())
        );
    }
}