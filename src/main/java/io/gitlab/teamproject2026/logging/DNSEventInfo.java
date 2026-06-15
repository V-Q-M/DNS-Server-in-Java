package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.RRType;


public record DNSEventInfo(DNSEventType eventType,
                           String id,
                           String domain,
                           RRType qType,
                           long latencyMs,
                           ResponseSource source) {

    public static DNSEventInfo info(DNSEventType type, String id, String domain, RRType qType, long latencyMs, ResponseSource source) {
        return new DNSEventInfo(type, id, domain, qType, latencyMs, source);
    }
}