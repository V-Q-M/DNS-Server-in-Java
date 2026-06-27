package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.*;



public record DNSEventDebug(
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
    public static DNSEventDebug debug(DNSEventType type, String id, String domain, RRType qType, long latencyMs,ResponseSource source,
                                 String strategy, String nsName, String nsHost, int attempt, String error, RCode rCode) {
        return new DNSEventDebug(type, id, domain, qType, latencyMs, source, strategy, nsName, nsHost, attempt, error, rCode);
    }

    public String toHumanReadable() {
        return String.format(
                " Strategy: %s. Nameserver: %s. Upstream: %s. Attempt: %s. Error message: %s. RCODE: %s",
                 strategy, nameserver, upstream, attempt, error, rCode
        );
    }
}
