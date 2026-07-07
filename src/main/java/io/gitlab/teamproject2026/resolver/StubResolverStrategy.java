package io.gitlab.teamproject2026.resolver;

/** Load-balancing strategies used by the stub resolver when selecting upstream DNS forwarders. */
public enum StubResolverStrategy {
    FAILOVER,
    ROUND_ROBIN;

    /**
     * Converts a configuration value into the corresponding strategy.
     *
     * @param strategy strategy name from the configuration
     * @return matching resolver strategy
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static StubResolverStrategy fromValue(String strategy) {
        if (strategy.equalsIgnoreCase("failover")) {
            return FAILOVER;
        }
        if (strategy.equalsIgnoreCase("round-robin")) {
            return ROUND_ROBIN;
        } else {
            throw new IllegalArgumentException("Invalid strategy: " + strategy);
        }
    }
}
