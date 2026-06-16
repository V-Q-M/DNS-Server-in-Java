package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;

import java.util.List;

/**
 * Immutable representation of the complete server configuration,
 * consisting of global options and configured DNS zones.
 */
public record Config(OptionSection options, List<ZoneSection> zones) {

}


