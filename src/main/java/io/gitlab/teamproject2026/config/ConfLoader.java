package io.gitlab.teamproject2026.config;

import java.util.ArrayList;
import java.util.List;

public class ConfLoader {
    public static Config fromAst(List<ConfNode> rootNodes){
        List<OptionConfig>options = new ArrayList<>();
        List<ZoneConfig>zones = new ArrayList<>();

        for (ConfNode node : rootNodes) {
            String name = node.name().toLowerCase();
            switch (name){
                case "options" -> options.add(parseOptionConfig(node));
                case "zone" -> zones.add(parseZoneConfig(node));
            }
        }

        return new Config(options, zones);
    }

    public static OptionConfig parseOptionConfig(ConfNode root) {
        String directoryPath = "";
        int port = 53;
        String ipAddress = "0.0.0.0";
        boolean recursion = false;

        for (ConfNode node : root.children()){
            if (node.name().equalsIgnoreCase("directory")){
                if(node.args().isEmpty()){ throw new IllegalArgumentException("options.directory requires a path"); }
                directoryPath = node.args().get(0);
            }
            if (node.name().equalsIgnoreCase("listen-on")){
                if (node.args().size() >= 2 && node.args().get(0).equalsIgnoreCase("port")) {
                    port = Integer.parseInt(node.args().get(1));
                }
                if (node.children().isEmpty()) { throw new IllegalArgumentException("options.listen-on requires an IP address or 'none'"); }

                ipAddress = node.children().get(0).name();
            }
            if (node.name().equalsIgnoreCase("recursion")){
                if(node.args().isEmpty()){ throw new IllegalArgumentException("options.recursion requires yes or no"); }
                recursion = node.args().get(0).equalsIgnoreCase("yes");
            }
        }
        return new OptionConfig(directoryPath, port, ipAddress, recursion);
    }

    public static ZoneConfig parseZoneConfig(ConfNode root) {
        if (root.args().isEmpty()){
            throw new IllegalArgumentException("zone requires a name");
        }

        String name = root.args().get(0);
        String file = "";
        ZoneType type = null;

        for (ConfNode node : root.children()){
            if (node.name().equalsIgnoreCase("file")){
                if (node.args().isEmpty()){
                    throw new IllegalArgumentException("zone " + name + " file requires a path");
                }
                file = node.args().get(0);
            }
            if (node.name().equals("type")){
                if (node.args().isEmpty()){
                    throw new IllegalArgumentException("zone " + name + " type requires a value");
                }
                type = ZoneType.fromValue(node.args().get(0).toLowerCase());
            }
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("zone " + name + " is missing file");
        }

        if (type == null) {
            throw new IllegalArgumentException("zone " + name + " is missing type");
        }

        return new ZoneConfig(name, file, type);
    }
}
