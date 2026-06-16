package io.gitlab.teamproject2026.resolver;

public enum ForwardMode {
    FORWARD_ONLY, FORWARD_FIRST;

    public static ForwardMode fromValue(String mode) {
        if (mode.equalsIgnoreCase("only")){
            return FORWARD_ONLY;
        }
        if (mode.equalsIgnoreCase("first")){
            return FORWARD_FIRST;
        } else {
            throw new IllegalArgumentException("Invalid forward-mode: " + mode);
        }
    }
}
