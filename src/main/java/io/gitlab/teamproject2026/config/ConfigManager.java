package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.config.interpreter.ConfInterpreter;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSection;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataSOA;
import io.gitlab.teamproject2026.zones.ZoneParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.gitlab.teamproject2026.AnsiColors.*;

/**
 * Utility class for loading, parsing, and validating {@link Config}
 * instances and {@link DNSZone}s, providing fallbacks to sensible defaults
 * when errors occur.
 */
public class ConfigManager {
    private static String zoneDir = "";

    public static Config defaultConfig() {
        return new Config(
                LoggingSection.defaultLogger(),
                OptionSection.defaultOptions(),
                List.of()
        );
    }

    public static Logger logger = new Logger(defaultConfig().logging());

    /**
     * Loads, parses, and interprets the server configuration file.
     * If the configuration cannot be read or contains errors, a default
     * {@link Config} is returned instead.
     *
     * @param configName path to the configuration file
     * @return the loaded {@link Config}, or the default configuration if loading fails
     */
    public static Config loadConfig(String configName) {
        Path confPath = Path.of(configName);

        String input;
        try {
            input = Files.readString(confPath);
        } catch (IOException e) {
            logger.log(
                (RED + "[CONFIG-ERROR]: Failed to read config file: " + confPath + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            logger.log(
                (YELLOW + "[CONFIG] WARNING: using default config" + RESET ),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.WARNING,
                true
            );
            return defaultConfig();
        }

        ConfParser parser = new ConfParser(input);
        if (parser.hasParserError()) {
            logger.log(
                (RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " Failed to parse config file: \"" + confPath.getFileName() + "\"" + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            logger.log(
                (YELLOW + "[CONFIG] WARNING: Using default config" + RESET ),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.WARNING,
                true
            );
            return defaultConfig();
        }

        ConfInterpreter interpreter = new ConfInterpreter();
        Config config = interpreter.fromAst(parser.getRootNodes());
        if (interpreter.hasInterpreterError()) {
            logger.log(
                (RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " Failed to interpret config file: \"" + confPath.getFileName() +"\"" + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            logger.log(
                (YELLOW + "[CONFIG] WARNING: Using default config" + RESET ),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            return defaultConfig();
        }

        // Update logger to use the configuration
        logger = new Logger(config.logging());
        return config;
    }

    // Config helper
    public static List<String> loadZonesFromConf(ZoneHandler zoneHandler, Config config) {
        OptionSection options = config.options();
        String dir = options.dir();
        List<ZoneSection> declaredZones = config.zones();

        List<String> zoneCatalog = new ArrayList<>();
        try{
            zoneHandler.clearAll();
            for (ZoneSection zone : declaredZones) {
                // Collect all zones specified in the config
                DNSZone loadedZone = ConfigManager.loadZoneFile(dir, zone.file(), zone.name());
                if (loadedZone != null) {
                    zoneHandler.insertZone(loadedZone);
                    zoneCatalog.add(zone.name());
                }
            }
        } catch (Exception e) {
            logger.log(
                    (RED + "[DATABASE-ERROR] Failed to insert zone files to the zone database, continuing without it" + RESET),
                    LoggingCategoryType.DEFAULT,
                    LoggingSeverity.ERROR,
                    true
            );
        }
        return zoneCatalog;
    }


    /**
     * Loads and parses a DNS zone file into a {@link DNSZone}.
     * Returns {@code null} if the file cannot be read, contains parser errors,
     * or is missing mandatory information such as the origin or default TTL.
     *
     * @param dir base directory used to resolve relative zone file paths
     * @param filePath path to the zone file
     * @param origin expected zone origin
     * @return the parsed zone, or {@code null} if loading fails
     */
    public static DNSZone loadZoneFile(String dir, String filePath, String origin) {
        zoneDir = dir;
        Path f = Path.of(filePath);
        if (!f.isAbsolute()){
            f = Path.of(dir).resolve(filePath);
        }
        logger.log(
            ("[ZONE] Parsing zone file: \"" + f.getFileName() + "\""),
            LoggingCategoryType.CONFIG,
            LoggingSeverity.INFO,
            true
        );

        String content;
        try {
            content = Files.readString(f);
        } catch (IOException e) {
            logger.log(
                (RED + "[ZONE-ERROR] Failed to read zone file: " + f + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            return null;
        }
        ZoneParser parser = new ZoneParser(content, origin);
        if (parser.hasParserError()) {
            logger.log(
                (RED + "[ZONE-ERROR] Failed to parse zone: " + f.getFileName() + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            return null;
        }

        List<ResourceRecord> zoneRecords = parser.getZoneRecords();
        String zoneOrigin;
        long defaultTtl;
        if (!parser.getOrigin().isEmpty()) {
            zoneOrigin = parser.getOrigin();
        } else {
            logger.log(
                (RED + "[ZONE-ERROR] No origin found in zone: " + f.getFileName() + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            return null;
        }
        if (parser.getTTL() != 0) {
            defaultTtl = parser.getTTL();
        } else {
            logger.log(
                (RED + "[ZONE-ERROR] No ttl found in zone: " + f.getFileName() + RESET),
                LoggingCategoryType.CONFIG,
                LoggingSeverity.ERROR,
                true
            );
            return null;
        }
        logger.log(
            (GREEN + "[ZONE] Found " + zoneRecords.size() + " Records in \"" + f.getFileName() + "\"" + RESET),
            LoggingCategoryType.CONFIG,
            LoggingSeverity.NOTICE,
            true
        );
        long serial = 0;
        long refresh = 0;
        for (ResourceRecord record : zoneRecords){
            if (record.getType() == RRType.SOA){
                RDataSOA soaData = (RDataSOA) record.getRdata();
                serial = soaData.getSerial();
                refresh = soaData.getRefresh();
                //IO.println("[ZONE] Found SOA with serial: " + serial + " and refresh: " + refresh);
                break;
            }
        }
        return new DNSZone(zoneOrigin, defaultTtl, serial, refresh, new ArrayList<>(zoneRecords));
    }

    public static String getZoneDir(){
        return zoneDir;
    }
    public static void setZoneDir(String dir){
        zoneDir = dir;
    }
}
