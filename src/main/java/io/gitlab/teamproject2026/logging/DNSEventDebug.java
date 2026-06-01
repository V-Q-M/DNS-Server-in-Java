package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.*;



public record DNSEventDebug(
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
    String error, 
    RCode rCode
) {



    // For detailed DEBUG logging
    public static DNSEventDebug debug(DNSEventType type, String id, String domain, RRType qType, long latencyMs,
                                 String strategy, String nsName, String nsHost, int attempt, String error, RCode rCode) {
        return new DNSEventDebug(type, id, domain, qType, latencyMs, strategy, nsName, nsHost, attempt, error, rCode);
    }
}
