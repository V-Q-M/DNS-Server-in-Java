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
        String forwardMode = "forward-only";
        List<String> forwarders = List.of("1.1.1.1", "8.8.8.8");
        String timeOut = "2000";
        String maxRetries = "2";
        String retryDelay = "100";
        String strategy = "failover";

        for (ConfNode node : root.children()){
            if (node.name().equalsIgnoreCase("directory")){
                if(node.args().isEmpty()){ throw new IllegalArgumentException("options.directory requires a path"); }
                directoryPath = node.args().get(0);
            } else if (node.name().equalsIgnoreCase("listen-on")){
                if (node.args().size() >= 2 && node.args().getFirst().equalsIgnoreCase("port")) {
                    port = Integer.parseInt(node.args().get(1));
                }
                if (node.children().isEmpty()) { throw new IllegalArgumentException("options.listen-on requires an IP address or 'none'"); }

                ipAddress = node.children().getFirst().name();
            } else if (node.name().equalsIgnoreCase("recursion")){
                if(node.args().isEmpty()){ throw new IllegalArgumentException("options.recursion requires yes or no"); }
                recursion = node.args().getFirst().equalsIgnoreCase("yes");
            } else if (node.name().equalsIgnoreCase("forwarders")){
                if (node.children().isEmpty()) {
                    throw new IllegalArgumentException("options.forwarders requires at least one server");
                }
                forwarders = node.children()
                        .stream()
                        .map(ConfNode::name)
                        .toList();
            } else if (node.name().equalsIgnoreCase("forward")){
                if (node.args().isEmpty()) { throw new IllegalArgumentException("forward requires one mode"); }
                forwardMode = node.args().getFirst();
            } else if (node.name().equalsIgnoreCase("resolver")){
                if (node.children().isEmpty()) { throw new IllegalArgumentException("options.resolvers requires at least one entry"); }
                for (ConfNode child : node.children()){
                    if (child.name().equalsIgnoreCase("timeout")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("timeout requires a value"); }
                        timeOut = child.args().getFirst();
                    } else if (child.name().equalsIgnoreCase("max-retries")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("max-retries requires a value"); }
                        timeOut = child.args().getFirst();
                    } else if (child.name().equalsIgnoreCase("retry-delay")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("retry-delay requires a value"); }
                        timeOut = child.args().getFirst();
                    } else if (child.name().equalsIgnoreCase("strategy")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("strategy requires a value"); }
                        timeOut = child.args().getFirst();
                    }
                }
            }
        }
        return new OptionConfig(directoryPath, port, ipAddress, recursion, forwardMode, forwarders,timeOut,maxRetries, retryDelay,strategy );
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
