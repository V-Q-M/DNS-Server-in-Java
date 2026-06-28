package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.*;



public record DNSEvent(
    DNSEventType eventType,
    String id, 
    String domain, 
    RRType qType, 
    long latencyMs,
    ResponseSource source,
    //debug fields
    String strategy, 
    String nameserver, 
    String upstream, 
    Integer attempt,
    String error, 
    RCode rCode
) {



    // For detailed DEBUG logging
    public static DNSEvent event(DNSEventType type, String id, String domain, RRType qType, long latencyMs,ResponseSource source,
                                 String strategy, String nsName, String nsHost, int attempt, String error, RCode rCode) {
        return new DNSEvent(type, id, domain, qType, latencyMs, source, strategy, nsName, nsHost, attempt, error, rCode);
    }

    public String toHumanReadable() {
        return String.format(
                "ID %s: %s ResourceRecord for '%s' needed %d ms (ResponseSource: %s)."+
                " Strategy: %s. Nameserver: %s. Upstream: %s. Attempt: %s. Error message: %s. RCODE: %s",
                id, qType, domain, latencyMs, source,
                 strategy, nameserver, upstream, attempt, error, rCode
        );
    }
}
