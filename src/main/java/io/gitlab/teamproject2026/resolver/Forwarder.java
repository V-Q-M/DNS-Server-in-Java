package io.gitlab.teamproject2026.resolver;

/**
 * Represents an upstream DNS server used by the stub resolver.
 *
 * @param host IP address or hostname of the upstream server
 * @param port DNS service port of the upstream server
 * @param name human-readable identifier used for logging and diagnostics
 */
public record Forwarder(String host, int port, String name) {
    /**
     * Returns a readable representation of the forwarder for logs.
     */
    @Override public String toString() {
        return name + "(" + host + ":" + port + ")";
    }
}
