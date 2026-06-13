package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.config.interpreter.ConfInterpreter;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RDataSOA;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;
import io.gitlab.teamproject2026.zones.ZoneParser;
import io.gitlab.teamproject2026.model.ResourceRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and stores DNS zones parsed from BIND-style zone files.
 */
public class ConfigManager {
    /** Loaded zones indexed by their normalized origin name. */
    private static String zoneDir = "";

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String BOLD   = "\u001B[1m";

    public static Config defaultConfig() {
        return new Config(
                OptionSection.defaultOptions(),
                List.of()
        );
    }

    public static Config loadConfig(String configName) {
        Path confPath = Path.of(configName);

        String input;
        try {
            input = Files.readString(confPath);
        } catch (IOException e) {
            System.err.println(RED + "[CONFIG-ERROR]: Failed to read config file: " + confPath + RESET);
            IO.println(YELLOW + "[CONFIG] WARNING: using default config" + RESET );
            return defaultConfig();
        }

        ConfParser parser = new ConfParser(input);
        if (parser.hasParserError()) {
            System.err.println(RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " Failed to parse config file: \"" + confPath.getFileName() + "\"" + RESET);
            System.err.println(YELLOW + "[CONFIG] WARNING: Using default config" + RESET );
            return defaultConfig();
        }

        ConfInterpreter interpreter = new ConfInterpreter();
        Config config = interpreter.fromAst(parser.getRootNodes());
        if (interpreter.hasInterpreterError()) {
            System.err.println(RED + BOLD + "[CONFIG-ERROR]" + RESET + RED + " Failed to interpret config file: \"" + confPath.getFileName() +"\"" + RESET);
            System.err.println(YELLOW + "[CONFIG] WARNING: Using default config" + RESET );
            return defaultConfig();
        }

        return config;
    }

    /**
     * Loads a zone file from the given path.
     * The file is parsed into a DNSZone and returned.
     * Files with parser errors, missing origins, or missing TTLs are skipped.
     *
     * @param filePath path containing a filePath as String
     * @returns DNSZone
     */
    public static DNSZone loadZoneFile(String dir, String filePath, String origin) {
        zoneDir = dir;
        Path f = Path.of(filePath);
        if (!f.isAbsolute()){
            f = Path.of(dir).resolve(filePath);
        }
        IO.println("[ZONE] Parsing zonefile: \"" + f.getFileName() + "\"");

        String content;
        try {
            content = Files.readString(f);
        } catch (IOException e) {
            System.err.println(RED + "[ZONE-ERROR] Failed to read zone file: " + f + RESET);
            return null;
        }
        ZoneParser parser = new ZoneParser(content, origin);
        if (parser.hasParserError()) {
            System.err.println(RED + "[ZONE-ERROR] Failed to parse zone: " + f.getFileName() + RESET);
            return null;
        }

        List<ResourceRecord> zoneRecords = parser.getZoneRecords();
        String zoneOrigin;
        long defaultTtl;
        if (!parser.getOrigin().isEmpty()) {
            zoneOrigin = parser.getOrigin();
        } else {
            System.err.println(RED + "[ZONE-ERROR] No origin found in zone: " + f.getFileName() + RESET);
            return null;
        }
        if (parser.getTTL() != 0) {
            defaultTtl = parser.getTTL();
        } else {
            System.err.println(RED + "[ZONE-ERROR] No ttl found in zone: " + f.getFileName() + RESET);
            return null;
        }
        IO.println(GREEN + "[ZONE] Found " + zoneRecords.size() + " Records in \"" + f.getFileName() + "\"" + RESET);
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
        DNSZone zone = new DNSZone(zoneOrigin, defaultTtl, serial, refresh, new ArrayList<>(zoneRecords));
        return zone;
    }

    public static String getZoneDir(){
        return zoneDir;
    }
    public static void setZoneDir(String dir){
        zoneDir = dir;
    }
}
