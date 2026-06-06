package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;

import java.util.List;

public record Config(OptionSection options, List<ZoneSection> zones) {

}


