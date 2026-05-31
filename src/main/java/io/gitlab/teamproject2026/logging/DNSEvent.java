package io.gitlab.teamproject2026.logging;
import io.gitlab.teamproject2026.model.RRType;
public record DNSEvent(
    DNSEventType eventType,
    String id,
    String domain,
    RRType qType,
    String dnsResolver, 
    long latencyMs,
    String error
) {
    public static DNSEvent of(DNSEventType eventType, DNSQueryInfo info, String dnsResolver, long latencyMs, String error) {
        return new DNSEvent(eventType, info.id(), info.domain(), info.qType(), dnsResolver, latencyMs, error);
    }
}