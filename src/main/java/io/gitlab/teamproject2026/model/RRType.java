package io.gitlab.teamproject2026.model;

public final class RRType {
    public static final int UNASSIGNED = -1;
    public static final int A = 1;
    public static final int NS = 2;
    public static final int CNAME = 5;
    public static final int SOA = 6;
    public static final int PTR = 12;
    public static final int MX = 15;
    public static final int TXT = 16;
    public static final int AAAA = 28;
    public static final int OPT = 41;
    public static final int DS = 43;
    public static final int RRSIG = 46;
    public static final int NSEC = 47;
    public static final int DNSKEY = 48;
    public static final int NSEC3 = 50;
    public static final int NSEC3PARAM = 51;

    public static String mapToString(int type) {
        return switch (type) {
            case A -> "A";
            case NS -> "NS";
            case CNAME -> "CNAME";
            case SOA -> "SOA";
            case PTR -> "PTR";
            case MX -> "MX";
            case TXT -> "TXT";
            case AAAA -> "AAAA";
            case OPT -> "OPT";
            case DS -> "DS";
            case RRSIG -> "RRSIG";
            case NSEC -> "NSEC";
            case DNSKEY -> "DNSKEY";
            case NSEC3 -> "NSEC3";
            case NSEC3PARAM -> "NSEC3PARAM";
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    public static int valueOf(String type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        return switch (type.toUpperCase()) {
            case "A" -> A;
            case "NS" -> NS;
            case "CNAME" -> CNAME;
            case "SOA" -> SOA;
            case "PTR" -> PTR;
            case "MX" -> MX;
            case "TXT" -> TXT;
            case "AAAA" -> AAAA;
            case "OPT" -> OPT;
            case "DS" -> DS;
            case "RRSIG" -> RRSIG;
            case "NSEC" -> NSEC;
            case "DNSKEY" -> DNSKEY;
            case "NSEC3" -> NSEC3;
            case "NSEC3PARAM" -> NSEC3PARAM;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }
}
