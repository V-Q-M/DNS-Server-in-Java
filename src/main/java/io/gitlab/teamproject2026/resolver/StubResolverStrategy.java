package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.RRType;

public enum StubResolverStrategy {
    FAILOVER, ROUND_ROBIN;

    public static StubResolverStrategy fromValue(String strategy) {
        if (strategy.equalsIgnoreCase("failover")){
            return FAILOVER;
        }
        if (strategy.equalsIgnoreCase("round-robin")){
            return ROUND_ROBIN;
        } else {
            throw new IllegalArgumentException("Invalid strategy: " + strategy);
        }
    }
}
