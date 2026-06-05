package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.config.interpreter.ConfNode;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ConfParserTest {
    @Test
    void testSimpleNamedConfTree() {
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
        roots.forEach(IO::println);

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
        assertEquals(List.of(), listenOnAddress.args());
        assertEquals(List.of(), listenOnAddress.children());

        ConfNode listenOnV6 = options.children().get(2);
        assertEquals("listen-on-v6", listenOnV6.name());
        assertEquals(List.of(), listenOnV6.args());
        assertEquals(1, listenOnV6.children().size());

        ConfNode listenOnV6Value = listenOnV6.children().get(0);
        assertEquals("none", listenOnV6Value.name());
        assertEquals(List.of(), listenOnV6Value.args());
        assertEquals(List.of(), listenOnV6Value.children());

        ConfNode recursion = options.children().get(3);
        assertEquals("recursion", recursion.name());
        assertEquals(List.of("no"), recursion.args());
        assertEquals(List.of(), recursion.children());

        ConfNode zone = roots.get(1);
        assertEquals("zone", zone.name());
        assertEquals(List.of("example.com"), zone.args());
        assertEquals(2, zone.children().size());

        ConfNode type = zone.children().get(0);
        assertEquals("type", type.name());
        assertEquals(List.of("primary"), type.args());
        assertEquals(List.of(), type.children());

        ConfNode file = zone.children().get(1);
        assertEquals("file", file.name());
        assertEquals(List.of("db.example.com"), file.args());
        assertEquals(List.of(), file.children());
    }}
