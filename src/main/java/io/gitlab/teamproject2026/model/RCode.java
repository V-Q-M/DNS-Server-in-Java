package io.gitlab.teamproject2026.model;

public final class RCode {
    public static final int UNASSIGNED = -1;
    public static final int NOERROR = 0;
    public static final int FORMERR = 1;
    public static final int SERVFAIL = 2;
    public static final int NXDOMAIN = 3;
    public static final int NOTIMP = 4;
    public static final int REFUSED = 5;

    public static String mapToString(int code) {
        return switch (code) {
            case UNASSIGNED -> "UNASSIGNED";
            case NOERROR -> "NOERROR";
            case FORMERR -> "FORMERR";
            case SERVFAIL -> "SERVFAIL";
            case NXDOMAIN -> "NXDOMAIN";
            case NOTIMP -> "NOTIMP";
            case REFUSED -> "REFUSED";
            default -> null;
        };
    }

    public static int valueOf(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code cannot be null");
        }
        return switch (code.toUpperCase()) {
            case "UNASSIGNED" -> UNASSIGNED;
            case "NOERROR" -> NOERROR;
            case "FORMERR" -> FORMERR;
            case "SERVFAIL" -> SERVFAIL;
            case "NXDOMAIN" -> NXDOMAIN;
            case "REFUSED" -> REFUSED;
            default -> throw new IllegalStateException("Unexpected value: " + code);
        };
    }
}
