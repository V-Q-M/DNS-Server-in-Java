package io.gitlab.teamproject2026.config;

import java.util.List;

public record OptionConfig(String dir, int port, String ipAddress, boolean recursionDesired, String forwardMode, List<String> forwarders, String resolverTimeout, String resolverMaxTries, String resolverMaxDelay, String resolverStrategy) {

}
