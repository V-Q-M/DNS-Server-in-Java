package io.gitlab.teamproject2026.config.logging;

public record LoggingChannel(
        String channelName,
        String fileName,
        LoggingSeverity severity

) {

}
