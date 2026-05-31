package io.gitlab.teamproject2026.logging;

import io.gitlab.teamproject2026.StubResolver.NameServer;
import io.gitlab.teamproject2026.StubResolver.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetAddress;
import java.util.Optional;


public class LoggingFeature {

    private static final Logger log = LoggerFactory.getLogger("DNS");

    

    /**
     * logs event for info
     */
    public static void log(DNSEvent event) {
        log.info("DNSEvent eventType={} id={} domain={} qType={} dnsResolver={} latencyMs={} error={}",
            event.eventType().name(),
            event.id(),
            event.domain(),
            event.qType(),
            event.dnsResolver() != null ? event.dnsResolver() : "NONE",
            event.latencyMs(),
            event.error() != null ? event.error() : "NO_ERROR"
        );
    }
}







