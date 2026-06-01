package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.*;



public record DNSEvent(
    DNSEventType eventType,
    String id, 
    String domain, 
    RRType qType, 
    long latencyMs,
    //debug fields
    String strategy, 
    String nameserver, 
    String upstream, 
    Integer attempt, 
    Boolean cacheHit, 
    String error, 
    RCode rCode
) {
    // For simple INFO logging
    public static DNSEvent info(DNSEventType type, String id, String domain, RRType qType, long latencyMs) {
        return new DNSEvent(type, id, domain, qType, latencyMs, null, null, null, null, null, null, null);
    }

    // For detailed DEBUG logging
    public static DNSEvent debug(DNSEventType type, String id, String domain, RRType qType, long latencyMs,
                                 String strategy, String nsName, String nsHost, int attempt, Boolean cacheHit, String error, RCode rCode) {
        return new DNSEvent(type, id, domain, qType, latencyMs, strategy, nsName, nsHost, attempt, cacheHit, error, rCode);
    }
}
