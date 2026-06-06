package io.gitlab.teamproject2026.resolver;

//host should be ip, name can be additional info
public record Forwarder(String host, int port, String name) {
    @Override public String toString() {
        return name + "(" + host + ":" + port + ")";
    }
}
