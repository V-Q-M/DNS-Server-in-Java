package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.RRType;


public record DNSEventInfo(DNSEventType eventType,
                           String id,
                           String domain,
                           RRType qType,
                           long latencyMs) {

    public static DNSEventInfo info(DNSEventType type, String id, String domain, RRType qType, long latencyMs) {
        return new DNSEventInfo(type, id, domain, qType, latencyMs);
    }
}