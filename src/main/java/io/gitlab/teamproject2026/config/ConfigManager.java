package io.gitlab.teamproject2026.config;

import io.gitlab.teamproject2026.DNSZone;
import io.gitlab.teamproject2026.config.interpreter.ConfInterpreter;
import io.gitlab.teamproject2026.config.interpreter.ConfParser;
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
            System.err.println(RED
                    + "[ZONE] ERROR: Failed to parse config file: "
                    + confPath.getFileName()
                    + RESET);
            return null;
        }
        return  ConfInterpreter.fromAst(confParser.getRootNodes());
    }

    /**
     * Loads a zone file from the given path.
     * The file is parsed into a DNSZone and returned.
     * Files with parser errors, missing origins, or missing TTLs are skipped.
     *
     * @param filePath path containing a filePath as String
     * @returns DNSZone
     */
    public static DNSZone loadZoneFile(String dir, String filePath, String origin) throws IOException {
        zoneDir = dir;
        Path f;
        if (filePath.startsWith("/")){
            f = Path.of(filePath);
        } else {
            f = Path.of(dir + "/" + filePath);
        }
        IO.println("[ZONE] Parsing zonefile: \"" + f.getFileName() + "\"");

        String content = Files.readString(f);
        ZoneParser parser = new ZoneParser(content, origin);
        if (parser.hasParserError()) {
            System.err.println(RED + "[ZONE] ERROR: Failed to parse zone: " + f.getFileName() + RESET);
            return null;
        }

        List<ResourceRecord> zoneRecords = parser.getZoneRecords();
        String zoneOrigin;
        long defaultTtl;
        if (!parser.getOrigin().isEmpty()) {
            zoneOrigin = parser.getOrigin();
        } else {
            System.err.println(RED + "[ZONE] ERROR: No origin found in zone: " + f.getFileName() + RESET);
            return null;
        }
        if (parser.getTTL() != 0) {
            defaultTtl = parser.getTTL();
        } else {
            System.err.println(RED + "[ZONE] ERROR: No ttl found in zone: " + f.getFileName() + RESET);
            return null;
        }
        IO.println(GREEN + "[ZONE] Found " + zoneRecords.size() + " Records in \"" + f.getFileName() + "\"" + RESET);
        DNSZone zone = new DNSZone(zoneOrigin, defaultTtl, new ArrayList<>(zoneRecords));

        return zone;
    }


    public static String getZoneDir(){
        return zoneDir;
    }
    public static void setZoneDir(String dir){
        zoneDir = dir;
    }
}
