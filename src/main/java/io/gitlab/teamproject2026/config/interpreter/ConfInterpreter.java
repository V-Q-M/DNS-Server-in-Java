package io.gitlab.teamproject2026.config.interpreter;

import io.gitlab.teamproject2026.config.Config;
import io.gitlab.teamproject2026.config.options.OptionSection;
import io.gitlab.teamproject2026.config.options.ResolverSection;
import io.gitlab.teamproject2026.config.zones.ZoneSection;
import io.gitlab.teamproject2026.config.zones.ZoneType;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;

import java.util.ArrayList;
import java.util.List;

public class ConfInterpreter {
    public static Config fromAst(List<ConfNode> rootNodes){
        OptionSection option = null;
        List<ZoneSection>zones = new ArrayList<>();

        for (ConfNode node : rootNodes) {
            String name = node.name().toLowerCase();
            switch (name){
                case "options" ->{
                    if (option != null){
                        throw new IllegalArgumentException("ERROR: Only one option section allowed but found more!");
                    }
                    option = parseOptionConfig(node);
                }
                case "zone" -> zones.add(parseZoneConfig(node));
            }
        }

        return new Config(option, zones);
    }

    public static OptionSection parseOptionConfig(ConfNode root) {
        // Default values
        String directoryPath = "";
        int port = 4242;
        String ipAddress = "0.0.0.0";
        boolean recursion = false;
        String forwardMode = "forward-only";
        List<String> forwarders = List.of("1.1.1.1", "8.8.8.8");

        int resolverTimeOut = 2000;
        int resolverMaxRetries = 2;
        long resolverRetryDelay = 100;
        StubResolverStrategy resolverStrategy = StubResolverStrategy.FAILOVER;

        for (ConfNode node : root.children()){
            if (node.name().equalsIgnoreCase("directory")){
                if(node.args().isEmpty()){ throw new IllegalArgumentException("options.directory requires a path"); }
                directoryPath = node.args().getFirst();
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
                        String rawTimeOut = child.args().getFirst();
                        try {
                            resolverTimeOut = Integer.parseInt(rawTimeOut);
                        } catch (NumberFormatException _){

                        }
                    } else if (child.name().equalsIgnoreCase("max-retries")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("max-retries requires a value"); }
                        String rawMaxRetries = child.args().getFirst();
                        try {
                            resolverMaxRetries = Integer.parseInt(rawMaxRetries);
                        } catch (NumberFormatException _){

                        }
                    } else if (child.name().equalsIgnoreCase("retry-delay")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("retry-delay requires a value"); }
                        String rawRetry = child.args().getFirst();
                        try {
                            resolverRetryDelay = Integer.parseInt(rawRetry);
                        } catch (NumberFormatException _){

                        }
                    } else if (child.name().equalsIgnoreCase("strategy")){
                        if (child.args().isEmpty()){ throw  new IllegalArgumentException("strategy requires a value"); }
                        resolverStrategy = StubResolverStrategy.fromValue(child.args().getFirst());
                    }
                }
            }
        }

        ResolverSection resolverSection = new ResolverSection( resolverTimeOut, resolverMaxRetries, resolverRetryDelay, resolverStrategy );

        return new OptionSection(directoryPath, port, ipAddress, recursion, forwardMode, forwarders, resolverSection);
    }

    public static ZoneSection parseZoneConfig(ConfNode root) {
        if (root.args().isEmpty()){
            throw new IllegalArgumentException("zone requires a name");
        }

        String name = root.args().getFirst();
        String file = "";
        ZoneType type = null;

        for (ConfNode node : root.children()){
            if (node.name().equalsIgnoreCase("file")){
                if (node.args().isEmpty()){
                    throw new IllegalArgumentException("zone " + name + " file requires a path");
                }
                file = node.args().getFirst();
            }
            if (node.name().equals("type")){
                if (node.args().isEmpty()){
                    throw new IllegalArgumentException("zone " + name + " type requires a value");
                }
                type = ZoneType.fromValue(node.args().getFirst().toLowerCase());
            }
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("zone " + name + " is missing file");
        }

        if (type == null) {
            throw new IllegalArgumentException("zone " + name + " is missing type");
        }

        return new ZoneSection(name, file, type);
    }
}
