package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.zones.ZoneParser;
import io.gitlab.teamproject2026.model.ResourceRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and stores DNS zones parsed from BIND-style zone files.
 * Provides lookup support for finding the authoritative zone
 * with the longest matching suffix for a queried domain name.
 */
public class ConfigManager {
    /** Loaded zones indexed by their normalized origin name. */
    private static Map<String, DNSZone> zones = new HashMap<>();

    static void main(String[] args) {
        // Has to come first
        loadConfig("named.conf");
    }

    public static Config loadConfig(String configName) {
       Path confPath = Path.of(configName);
       String input = "";
       try {
           input = Files.readString(confPath);
       } catch (IOException e){
           e.printStackTrace();
       }
       ConfParser confParser = new ConfParser(input);
        if (confParser.hasParserError()) {
            System.err.println("ERROR: Failed to parse config file: " + confPath.getFileName());
            return null;
        }
        return  ConfLoader.fromAst(confParser.getRootNodes());
    }

    /**
     * Loads a zone file from the given path.
     * The file is parsed into a DNSZone and returned.
     * Files with parser errors, missing origins, or missing TTLs are skipped.
     *
     * @param filePath path containing a filePath as String
     * @returns DNSZone
     */
    public static DNSZone loadZoneFile(String filePath, String origin) throws IOException {
        Path f = Path.of(filePath);
        IO.println("Parsing zonefile: \"" + f.getFileName() + "\"");

        String content = Files.readString(f);
        ZoneParser parser = new ZoneParser(content, origin);
        if (parser.hasParserError()) {
            System.err.println("ERROR: Failed to parse zone: " + f.getFileName());
            return null;
        }

        List<ResourceRecord> zoneRecords = parser.getZoneRecords();
        String zoneOrigin;
        long defaultTtl;
        if (!parser.getOrigin().isEmpty()) {
            zoneOrigin = parser.getOrigin();
        } else {
            System.err.println("ERROR: No origin found in zone: " + f.getFileName());
            return null;
        }
        if (parser.getTTL() != 0) {
            defaultTtl = parser.getTTL();
        } else {
            System.err.println("ERROR: No ttl found in zone: " + f.getFileName());
            return null;
        }
        IO.println("Found " + zoneRecords.size() + " Records in \"" + f.getFileName() + "\"");

        DNSZone zone = new DNSZone(zoneOrigin, defaultTtl, new ArrayList<>(zoneRecords));

        storeZones(zone);
        return zone;
    }

    public static void storeZones(DNSZone zone){
        zones.put(zone.getOrigin(), zone);
        //IO.println("Zones: " + zones);
    }
}
