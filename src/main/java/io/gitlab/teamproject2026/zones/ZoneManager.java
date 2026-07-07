package io.gitlab.teamproject2026.zones;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.rdata.RDataSOA;
import io.gitlab.teamproject2026.server.ServerContext;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoneManager implements Runnable {

    private final ZoneHandler zoneHandler;
    private final Config serverConfig;
    private final List<String> zoneCatalog;
    private final long startTime;
    private final Logger logger;

    public ZoneManager(ServerContext context) {
        this.zoneHandler = context.zoneHandler();
        this.serverConfig = context.serverConfig();
        this.zoneCatalog = context.zoneCatalog();
        this.logger = context.logger();
        this.startTime = context.startTime();
    }

    @Override
    public void run() {
        handleZoneRefresh();
    }

    // Check if it's time to refresh the zones and perform the refresh if needed
    private void handleZoneRefresh() {
        OptionSection options = serverConfig.options();
        String dir = options.dir();
        Map<String, Long> lastRefreshZoneBy = new HashMap<>();

        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            for (String zone : zoneCatalog) {
                try {
                    RDataSOA zoneSoa = zoneHandler.lookupZoneSoa(zone);
                    long refreshMillis = zoneSoa.getRefresh() * 1000L;
                    long lastRefresh = lastRefreshZoneBy.getOrDefault(zone, startTime);
                    if (now - lastRefresh < refreshMillis) {
                        continue;
                    }
                    logger.log(
                            (BLUE + "[ZONE] Starting zone refresh for \"" + zone + "\"" + RESET),
                            LoggingCategoryType.CONFIG,
                            LoggingSeverity.NOTICE,
                            true);

                    lastRefreshZoneBy.put(zone, now);

                    String file = findZoneFile(zone);
                    if (file == null) {
                        continue;
                    }

                    DNSZone newLoadedZone = ConfigManager.loadZoneFile(dir, file, zone);

                    if (newLoadedZone != null) {
                        logger.log(
                                ("[ZONE] Comparing local serial: " + newLoadedZone.serial() + " to cached serial: "
                                        + zoneSoa.getSerial()),
                                LoggingCategoryType.CONFIG,
                                LoggingSeverity.INFO,
                                true);
                        if (newLoadedZone.serial() != zoneSoa.getSerial()) {
                            logger.log(
                                    (YELLOW + "[ZONE] File: \"" + file + "\" changed, reloading zone..." + RESET),
                                    LoggingCategoryType.CONFIG,
                                    LoggingSeverity.NOTICE,
                                    true);
                            zoneHandler.replaceZone(newLoadedZone);
                        } else {
                            logger.log(
                                    (GREEN + "[ZONE] Serials match -> Nothing to do" + RESET),
                                    LoggingCategoryType.CONFIG,
                                    LoggingSeverity.INFO,
                                    true);
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (SQLException e) {
                    logger.log(
                            (RED + "[DATABASE-ERROR] Couldn't query SOA record of: \"" + zone + "\"" + RESET),
                            LoggingCategoryType.QUERY_ERRORS,
                            LoggingSeverity.ERROR,
                            true);
                }
            }
        }
    }

    private String findZoneFile(String origin) {
        for (ZoneSection zoneSection : serverConfig.zones()) {
            if (zoneSection.name().equalsIgnoreCase(origin)) {
                return zoneSection.file();
            }
        }
        return null;
    }
}
