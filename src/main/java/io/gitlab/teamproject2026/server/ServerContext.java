package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.DashboardHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.Logger;
import java.util.List;

public record ServerContext(
        CacheHandler cacheHandler,
        ZoneHandler zoneHandler,
        DashboardHandler dashboardHandler,
        Logger logger,
        DNSResolutionPipeline resolutionPipeline,
        DNSResponseBuilder responseBuilder,
        Config serverConfig,
        List<String> zoneCatalog,
        int ednsUdpSizeLimit,
        boolean dnssecEnable,
        boolean dnssecValidate,
        long startTime) {}
