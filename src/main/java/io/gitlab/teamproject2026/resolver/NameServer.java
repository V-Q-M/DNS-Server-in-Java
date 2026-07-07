package io.gitlab.teamproject2026.resolver;

/**
 * Represents a nameserver (forwarder, recursive resolver, authoritative server)
 *
 * @param host IP address or hostname of the nameserver
 * @param port DNS service port of the nameserver
 * @param name human-readable identifier used for logging and diagnostics
 */
public record NameServer(String host, int port, String name) {
    /** Returns a readable representation of the nameserver for logs. */
    @Override
    public String toString() {
        return name + "(" + host + ":" + port + ")";
    }
}
