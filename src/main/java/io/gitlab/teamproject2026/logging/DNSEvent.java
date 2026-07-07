package io.gitlab.teamproject2026.logging;

import io.gitlab.teamproject2026.model.*;

public record DNSEvent(
        DNSEventType eventType,
        String id,
        String domain,
        int qType,
        long latencyMs,
        ResponseSource source,
        // debug fields
        String strategy,
        String nameserver,
        String upstream,
        Integer attempt,
        String error,
        int rCode) {

    // For detailed logging of query/response
    public static DNSEvent event(
            DNSEventType type,
            String id,
            String domain,
            int qType,
            long latencyMs,
            ResponseSource source,
            String strategy,
            String nsName,
            String nsHost,
            int attempt,
            String error,
            int rCode) {
        return new DNSEvent(
                type, id, domain, qType, latencyMs, source, strategy, nsName, nsHost, attempt, error, rCode);
    }

    /**
     * transforms DNSEvent into human readable sentence
     *
     * @return String describing response/query
     */
    public String toHumanReadable() {
        return String.format(
                "%s for ID %s: %s ResourceRecord for '%s' needed %d ms (ResponseSource: %s)."
                        + " Strategy: %s. Nameserver: %s. Upstream: %s. Attempt: %s. Error message: %s. RCODE: %s",
                eventType, id, qType, domain, latencyMs, source, strategy, nameserver, upstream, attempt, error, rCode);
    }
}
