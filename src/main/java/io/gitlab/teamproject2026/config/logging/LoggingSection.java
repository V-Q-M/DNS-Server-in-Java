package io.gitlab.teamproject2026.config.logging;

import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;

import java.util.List;
import java.util.Map;

/**
 * Represents the complete {@code logging} section of the configuration.
 * <p>
 * Contains all configured logging channels as well as the mappings from
 * logging categories to their associated channels. The logger uses this
 * information to determine where messages should be written based on their
 * category and severity.
 *
 * @param channels the configured logging channels
 * @param categories the mappings from logging categories to their associated
 *                   logging channels
 */
public record LoggingSection(
        List<LoggingChannel> channels,
        Map<LoggingCategoryType, LoggingCategory> categories
) {
    /** Gets called when the config fails */
    public static LoggingSection defaultLogger() {

        LoggingChannel defaultLog = new LoggingChannel(
                "default_log",
                "log/default/default.log",
                LoggingSeverity.INFO,
                true,
                true,
                true
        );

        LoggingChannel errorLog = new LoggingChannel(
                "error_log",
                "log/default/default-error.log",
                LoggingSeverity.ERROR,
                true,
                true,
                true
        );

        LoggingChannel criticalLog = new LoggingChannel(
                "critical_log",
                "log/default/default-critical.log",
                LoggingSeverity.CRITICAL,
                true,
                true,
                true
        );

        return new LoggingSection(
                List.of(defaultLog, errorLog, criticalLog),
                Map.of(
                        LoggingCategoryType.DEFAULT,
                        new LoggingCategory(
                                LoggingCategoryType.DEFAULT,
                                List.of(defaultLog, errorLog, criticalLog)
                        ),
                        LoggingCategoryType.GENERAL,
                        new LoggingCategory(
                                LoggingCategoryType.GENERAL,
                                List.of(defaultLog, errorLog, criticalLog)
                        ),
                        LoggingCategoryType.CONFIG,
                        new LoggingCategory(
                                LoggingCategoryType.CONFIG,
                                List.of(defaultLog, errorLog, criticalLog)
                        ),
                        LoggingCategoryType.QUERIES,
                        new LoggingCategory(
                                LoggingCategoryType.QUERIES,
                                List.of(defaultLog, errorLog, criticalLog)
                        ),
                        LoggingCategoryType.QUERY_ERRORS,
                        new LoggingCategory(
                                LoggingCategoryType.QUERY_ERRORS,
                                List.of(defaultLog, errorLog, criticalLog)
                        )
                )
        );
    }

}