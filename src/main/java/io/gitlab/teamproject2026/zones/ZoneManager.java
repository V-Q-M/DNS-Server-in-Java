package io.gitlab.teamproject2026.zones;

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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoneManager implements Runnable {

    private ZoneHandler zoneHandler;
    private Config serverConfig;
    private List<String> zoneCatalog;
    private long startTime;
    private Logger logger;

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String PINK = "\u001B[95m";

    public ZoneManager(ZoneHandler zoneHandler, Config serverConfig, List<String> zoneCatalog, long startTime, Logger logger){
        this.zoneHandler = zoneHandler;
        this.serverConfig = serverConfig;
        this.zoneCatalog = zoneCatalog;
        this.startTime = startTime;
        this.logger = logger;
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
                            true
                    );

                    lastRefreshZoneBy.put(zone, now);

                    String file = findZoneFile(zone);
                    if (file == null) {
                        continue;
                    }

                    DNSZone newLoadedZone = ConfigManager.loadZoneFile(dir, file, zone);

                    if (newLoadedZone != null) {
                        logger.log(
                                ("[ZONE] Comparing local serial: " + newLoadedZone.serial() + " to cached serial: " + zoneSoa.getSerial()),
                                LoggingCategoryType.CONFIG,
                                LoggingSeverity.INFO,
                                true
                        );
                        if (newLoadedZone.serial() != zoneSoa.getSerial()) {
                            logger.log(
                                    (YELLOW + "[ZONE] File: \"" + file + "\" changed, reloading zone..." + RESET),
                                    LoggingCategoryType.CONFIG,
                                    LoggingSeverity.NOTICE,
                                    true
                            );
                            zoneHandler.replaceZone(newLoadedZone);
                        } else {
                            logger.log(
                                    (GREEN + "[ZONE] Serials match -> Nothing to do" + RESET),
                                    LoggingCategoryType.CONFIG,
                                    LoggingSeverity.INFO,
                                    true
                            );
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
                            true
                    );

                }
            }
        }
    }

    private String findZoneFile(String origin){
        for (ZoneSection zoneSection : serverConfig.zones()){
            if (zoneSection.name().equalsIgnoreCase(origin)){
                return zoneSection.file();
            }
        }
        return null;
    }


}
