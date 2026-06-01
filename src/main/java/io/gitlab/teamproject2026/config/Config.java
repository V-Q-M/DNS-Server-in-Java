package io.gitlab.teamproject2026.config;

import java.util.List;

public record Config(List<OptionConfig> options, List<ZoneConfig> zones) {

}
