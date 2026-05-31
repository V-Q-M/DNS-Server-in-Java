package io.gitlab.teamproject2026.logging;

import io.gitlab.teamproject2026.StubResolver.NameServer;
import io.gitlab.teamproject2026.StubResolver.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetAddress;


public class LoggingFeature {

    private static final Logger stubLog = LoggerFactory.getLogger("DNS.Stub");
    private static final Logger DNSServerLog = LoggerFactory.getLogger("DNS.Server");//not needed yet

    // stub logs/DNS_server logs
    public static void logAttempt(int attempt, int maxAttempts, NameServer server, int Id, byte[] hexData) {
        stubLog.info("Attempt {}/{} => {}", attempt, maxAttempts, server);
        stubLog.debug("[ID: {}] Outbound packet hex payload: {}", Id, hexData);
    }

    public static void logSuccess(NameServer server, long latencyMs, int Id) {
        stubLog.info("Reply from {} in {} ms", server, latencyMs);
        stubLog.debug("[ID: {}] Upstream lookup completed successfully.", Id);
    }

    public static void logTimeout(NameServer server, long latencyMs) {
        stubLog.warn("Timeout from {} after {} ms", server, latencyMs);
    }

    public static void logStrategyTransition(Strategy strategy) {
        if (strategy == Strategy.FAILOVER) {
            stubLog.info("Switching to next available nameserver..");
        } else {
            stubLog.info("Retrying current configuration target..");
        }
    }

    public static void logIOError(NameServer server, long latencyMs, String error) {
        stubLog.error("I/O error from {} after {} ms: {}", server, latencyMs, error);
    }

    public static void logFailureExhausted(int maxAttempts) {
        stubLog.error("All {} attempt(s) exhausted - returning SERVFAIL", maxAttempts);
    }


    public static void logServerStartup(String bindIP, int portNumber) {
        DNSServerLog.info("UDP server listening on {}:{}", bindIP, portNumber);
    }

    public static void logServerStartupError(String message) {
        DNSServerLog.error("Server startup initialization failed: {}", message);
    }

    public static void logConfigWarning(String message) {
        DNSServerLog.warn("Config warning: {}", message);
    }

    // Fixed: Added missing domainName parameter and integrated mixed-level debugging
    public static void logIncomingPacket(InetAddress stubAddress, int stubPort, String domainName, String qType, int Id, byte[] hexData) {
        DNSServerLog.info("Received [{}] query for '{}' from {}:{}", qType, domainName, stubAddress, stubPort);
        DNSServerLog.debug("[ID: {}] Inbound Hex payload: {}", Id, hexData);
    }

    public static void logAssembledPacket(Object oopPacket) {
        if (DNSServerLog.isDebugEnabled()) {  //following needs more computing-> can be turned off for better performance
            DNSServerLog.debug("Assembled Packet Structure:\n{}", oopPacket);
        }
    }

    public static void logCacheHit(String domainName, String qType) {
        DNSServerLog.info("[CACHE HIT] Served [{}] '{}' directly from local storage.", qType, domainName);
    }

    public static void logCacheMiss(String domainName, String qType) {
        DNSServerLog.info("[CACHE MISS] [{}] '{}' not cached. Forwarding query...", qType, domainName);
    }

    // Combined INFO/DEBUG: Clean high-level metrics with deep data on demand
    public static void logOutgoingResponse(InetAddress stubAddress, int stubPort, String domainName, int Id, byte[] hexData) {
        DNSServerLog.info("Sent response for '{}' back to {}:{}", domainName, stubAddress, stubPort);
        DNSServerLog.debug("[ID: {}] Outbound response Hex payload: {}", Id, hexData);
    }

    public static void logServerException(InetAddress stubAddress, int stubPort, String error) {
        DNSServerLog.error("Error handling packet from {}:{} - {}", stubAddress, stubPort, error);
    }
}

