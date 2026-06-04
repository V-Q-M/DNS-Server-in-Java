package io.gitlab.teamproject2026.zones;

import io.gitlab.teamproject2026.config.ConfigManager;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Parser for BIND-style DNS zone files.
 * Converts lexer tokens into structured DNS resource records.
 * Supports common DNS record types, DNSSEC record types, multiline records,
 * owner-name inheritance, $ORIGIN, $TTL, TTL suffixes, and line-aware diagnostics.
 */
public class ZoneParser {
    /** All tokens produced by the lexer before parsing begins.*/
    private final List<ZoneToken> tokens = new ArrayList<>();
    /** Tokens belonging to the currently parsed logical resource record.*/
    private final List<ZoneToken> tokenBuffer = new ArrayList<>();
    /** Parsed resource records from the zone file.*/
    private final List<ResourceRecord> zoneRecords = new ArrayList<>();

    private String input; // Allow it to be freed later
    //private String directoryName = "zones/";

    /** Current zone origin used to resolve relative domain names. */
    private String origin = "";
    /** Default TTL from the $TTL directive. */
    private String defaultTtl = "";
    /** Previous owner name used for BIND-style owner inheritance. */
    private String prevName = "";
    /** Use previous owner name. */
    private boolean currentRecordStartsWithSpace = false;
    /** Whether this zone already contains an SOA record. */
    private boolean seenSoaRecord = false;

    private boolean parserError = false;

    private static final Pattern TTL_PATTERN =
            Pattern.compile("\\d+[smhdwSMHDW]?");

    public ZoneParser(String input) {
        this.input = input;
        startParser();
    }
    public ZoneParser(String input, boolean checkForSOA) {
        this.input = input;
        this.seenSoaRecord = !checkForSOA; // Act as though a soa already exists
        startParser();
    }

    public ZoneParser(String input, String origin) {
        this.input = input;
        this.origin = origin;
        startParser();
    }
    public ZoneParser(String input, String origin, String defaultTtl, boolean checkForSOA) {
        this.input = input;
        this.origin = origin;
        this.defaultTtl = defaultTtl;
        this.seenSoaRecord = !checkForSOA; // Act as tho a soa already exists
        startParser();
    }


    public List<ResourceRecord> getZoneRecords() {
        return List.copyOf(zoneRecords);
    }

    public String getOrigin() {
        return this.origin;
    }

    public long getTTL() { return defaultTtl.isEmpty() ? 0 : Long.parseLong(defaultTtl); }

    private static final EnumSet<ZoneTokenType> RR_TYPES =
            EnumSet.of(
                    ZoneTokenType.A_RECORD,
                    ZoneTokenType.AAAA_RECORD,
                    ZoneTokenType.MX_RECORD,
                    ZoneTokenType.NS_RECORD,
                    ZoneTokenType.CNAME_RECORD,
                    ZoneTokenType.TXT_RECORD,
                    ZoneTokenType.SOA_RECORD,
                    ZoneTokenType.PTR_RECORD
            );
    private static final EnumSet<ZoneTokenType> DNSSEC_TYPES =
            EnumSet.of(
                    ZoneTokenType.DNSKEY_RECORD,
                    ZoneTokenType.DS_RECORD,
                    ZoneTokenType.RRSIG_RECORD,
                    ZoneTokenType.NSEC_RECORD,
                    ZoneTokenType.NSEC3_RECORD,
                    ZoneTokenType.NSEC3PARAM_RECORD
            );

    private static final EnumSet<ZoneTokenType> ALL_RR_TYPES =
            EnumSet.copyOf(RR_TYPES);
    static {
        ALL_RR_TYPES.addAll(DNSSEC_TYPES);
    }

    /**
     * Starts lexing and parsing the input zone file.
     * The original input reference is released after lexer creation.
     */
    private void startParser() {
        ZoneLexer lexer = new ZoneLexer(input);
        input = null; // Parser no longer needs its own reference

        // Collect Tokens
        while (true) {
            ZoneToken tok = lexer.nextToken();
            tokens.add(tok);
            //    IO.println(tok.getLiteral() + ":" +tok.getType());
            if (tok.type() == ZoneTokenType.EOF) {
                break;
            }
        }
        parseTokens();
        tokens.clear();
        /*
        for (ResourceRecord r : zoneRecords) {
            IO.println(r.getType() + ":" + r);
        }
         */
    }

    /**
     * Groups tokens into logical resource records and handles zone directives.
     */
    private void parseTokens() {
        tokenBuffer.clear();
        boolean bracketOpen = false;

        for (int i = 0; i < tokens.size(); i++) {
            if (parserError) {
                return;
            }

            ZoneToken t = tokens.get(i);
            //IO.println(t.getLiteral() + ":" + t.getType());

            switch (t.type()){
                case ORIGIN -> {
                    if (!tokenBuffer.isEmpty() || bracketOpen) {
                        error("$ORIGIN not allowed inside record", t.lineNumber());
                        return;
                    }
                    i = parseOriginDirective(i);
                }
                case TTL -> {
                    if (!tokenBuffer.isEmpty() || bracketOpen) {
                        error("$TTL not allowed inside record",t.lineNumber());
                        return;
                    }
                    i = parseTtlDirective(i);
                }
                case INCLUDE -> {
                    if (!tokenBuffer.isEmpty() || bracketOpen) {
                        error("$INCLUDE not allowed inside record",t.lineNumber());
                        return;
                    }
                    i = parseIncludeDirective(i);
                }
                case LPAREN -> {
                    if(bracketOpen) {
                        error("Nested parentheses are not allowed",t.lineNumber());
                        return;
                    }
                    bracketOpen = true;
                }

                case RPAREN -> {
                    if (!bracketOpen){
                        error("Closed parenthesis without opening parenthesis",t.lineNumber());
                        return;
                    }
                    bracketOpen = false;
                    //finishRecordIfPresent();
                }
                case SPACE -> {
                    if (tokenBuffer.isEmpty()) {
                        currentRecordStartsWithSpace = true;
                    }
                }

                case NEWLINE -> {
                    if(!bracketOpen){
                        finishRecordIfPresent();
                        currentRecordStartsWithSpace = false;
                    }
                }

                case EOF -> {
                    if (bracketOpen) {
                        error("Unclosed parentheses",t.lineNumber());
                        return;
                    }
                    finishRecordIfPresent();
                    currentRecordStartsWithSpace = false;
                }

                default -> {
                    if (isRecordToken(t)) {
                        tokenBuffer.add(t);
                    } else {
                        error("Unexpected token: " + t.literal(), t.lineNumber());
                        return;
                    }
                }
            }
        }
        if (!parserError && !seenSoaRecord) {
            error("Zone must contain exactly one SOA record", tokens.getLast().lineNumber());
        }
    }

    private int skipSpaces(int i) {
        while (i < tokens.size() && tokens.get(i).type() == ZoneTokenType.SPACE) {
            i++;
        }
        return i;
    }

    private int parseOriginDirective(int i){
        int valueIndex = skipSpaces(i + 1);

        if (valueIndex >= tokens.size()){
            error("No origin found",tokens.get(i).lineNumber());
            return i;
        }
        ZoneToken originToken = tokens.get(valueIndex);
        if (originToken.type() != ZoneTokenType.ATOM) {
            error("Invalid origin name: " + originToken.literal(), originToken.lineNumber());
            return i;
        }

        String normalized = normalizeOrigin(originToken.literal());
        parseDomainLabels(normalized, "$ORIGIN", originToken.lineNumber());
        if (parserError) return i;
        origin = normalized;
        return valueIndex;
    }

    private int parseTtlDirective(int i ) {
        int valueIndex = skipSpaces(i + 1);
        if (valueIndex >= tokens.size()) {
            error("No TTL found",tokens.get(i).lineNumber());
            return i;
        }
        ZoneToken ttlToken = tokens.get(valueIndex);
        if (!isTTLToken(ttlToken)) {
            error("Invalid TTL: " + ttlToken.literal(), ttlToken.lineNumber());
            return i;
        }

        long ttl = parseTTL(ttlToken, "$TTL");
        if (parserError) { return i; }

        defaultTtl = String.valueOf(ttl);
        return valueIndex;
    }

    private int parseIncludeDirective(int i) {
        int includeIndex = skipSpaces(i + 1);

        if (includeIndex >= tokens.size()) {
            error("No INCLUDE FILE found", tokens.get(i).lineNumber());
            return i;
        }

        ZoneToken includeToken = tokens.get(includeIndex);

        if (includeToken.type() != ZoneTokenType.ATOM && includeToken.type() != ZoneTokenType.TXT) {
            error("Invalid file name: " + includeToken.literal(), includeToken.lineNumber());
            return i;
        }

        int nextIndex = skipSpaces(includeIndex + 1);
        int returnIndex = includeIndex;

        String subOrigin = origin;

        if (nextIndex < tokens.size()
                && tokens.get(nextIndex).type() != ZoneTokenType.NEWLINE
                && tokens.get(nextIndex).type() != ZoneTokenType.EOF) {
            ZoneToken subOriginToken = tokens.get(nextIndex);

            if (subOriginToken.type() != ZoneTokenType.ATOM) {
                error("Invalid new origin: " + subOriginToken.literal(), subOriginToken.lineNumber());
                return i;
            }

            subOrigin = subOriginToken.literal();
            returnIndex = nextIndex;
        }

        String subInput;

        try {
            subInput = Files.readString(Path.of(ConfigManager.getZoneDir() + "/" + includeToken.literal()));
        } catch (IOException e) {
            error("Failed to read file: " + includeToken.literal(), includeToken.lineNumber());
            return i;
        }

        ZoneParser subParser = new ZoneParser(subInput, subOrigin, this.defaultTtl, false);

        if (subParser.hasParserError()) {
            error("Failed to parse zone: " + includeToken.literal(), includeToken.lineNumber());
            return i;
        }

        this.zoneRecords.addAll(subParser.getZoneRecords());
        return returnIndex;
    }

    private void finishRecordIfPresent() {
        if (tokenBuffer.isEmpty()) return;
        buildRecord();
        tokenBuffer.clear();
    }

    private boolean isRecordToken(ZoneToken t) {
        return t.type() == ZoneTokenType.ATOM
                || t.type() == ZoneTokenType.TXT
                || t.type() == ZoneTokenType.NUMBER
                || t.type() == ZoneTokenType.IPV4_ADDRESS
                || t.type() == ZoneTokenType.IPV6_ADDRESS
                || t.type() == ZoneTokenType.CLASS
                || ALL_RR_TYPES.contains(t.type());
    }


    private boolean isTTLToken(ZoneToken t) {
        if (t.type() != ZoneTokenType.NUMBER && t.type() != ZoneTokenType.ATOM) {
            return false;
        }
        return TTL_PATTERN.matcher(t.literal()).matches();
    }

    private String normalizeOrigin(String value) {
        if (value.endsWith(".")) { return value; }
        return  value + ".";
    }

    /**
     * Parses the current token buffer into a ResourceRecord.
     */
    private void buildRecord () {
        if (tokenBuffer.isEmpty()) {
            return;
        }

        String recordName;
        String recordTtlLocal = "";
        RRClass recordClass = RRClass.IN;
        RRType recordType = null;

        int i = 0;

        if (currentRecordStartsWithSpace) {
            recordName = prevName;
        } else {
            recordName = normalizeDomainName(tokenBuffer.get(i).literal());
            i++;
        }

        if (recordName == null || recordName.isEmpty()) {
            error("Record has no owner name and no previous owner name", tokenBuffer.getLast().lineNumber());
            return;
        }

        while (i < tokenBuffer.size()) {
            ZoneToken t = tokenBuffer.get(i);

            if (isRRType(t)) {
                recordType = RRType.valueOf(t.literal());
                i++;
                break;
            }

            if (t.type() == ZoneTokenType.CLASS) {
                if (!t.literal().equals("IN")) {
                    error("Unsupported class: " + t.literal(), t.lineNumber());
                    return;
                }

                recordClass = RRClass.IN;
                i++;
                continue;
            }

            if (isTTLToken(t)) {
                if (!recordTtlLocal.isEmpty()) {
                    error("Duplicate TTL in record", t.lineNumber());
                    return;
                }

                long ttl = parseTTL(t, "record TTL");
                if (parserError) return;

                recordTtlLocal = String.valueOf(ttl);
                i++;
                continue;
            }
            error("Unexpected token before RR type: " + t.literal(), t.lineNumber());
            return;
        }
        if (recordType == null) {
            error("Record has no RR type", tokenBuffer.getLast().lineNumber());
            return;
        }
        if (recordType == RRType.SOA) {
            if (seenSoaRecord) {
                error("Zone must not contain more than one SOA record", tokenBuffer.getLast().lineNumber());
                return;
            }
            seenSoaRecord = true;
        }

        if (i >= tokenBuffer.size()) {
            error("Missing RDATA for " + recordType + " record", tokenBuffer.getLast().lineNumber());
            return;
        }

        long ttlValue;
        if (!recordTtlLocal.isEmpty()) {
            ttlValue = Long.parseLong(recordTtlLocal);
        } else if (!defaultTtl.isEmpty()) {
            ttlValue = Long.parseLong(defaultTtl);
        } else {
            ttlValue = 0;
        }

        String[] name = parseDomainLabels(recordName, "Record name", tokenBuffer.getLast().lineNumber());
        if (parserError) return;

        int rdataStart = i;

        RData rdata = parseRdata(recordType, rdataStart);

        ResourceRecord record = new ResourceRecord(name, recordClass, ttlValue, rdata);

        if (!parserError) {
            prevName = recordName;
            zoneRecords.add(record);
        }
    }

    /**
     * Parses RDATA based on the already identified RR type.
     */
    private RData parseRdata(RRType type, int rdataStart){
        return switch (type) {
            case A -> parseIPv4(rdataStart);
            case AAAA -> parseIPv6(rdataStart);
            case NS, CNAME, PTR -> parseDomainName(type, rdataStart);
            case MX -> parseMX(rdataStart);
            case TXT -> parseTXT(rdataStart);
            case SOA -> parseSOA(rdataStart);
            // DNSSEC
            /* Not implemented yet
            case DNSKEY -> parseDNSKEY(rdataStart);
            case DS -> parseDS(rdataStart);
            case RRSIG -> parseRRSIG(rdataStart);
            case NSEC -> parseNSEC(rdataStart);
            case NSEC3 -> parseNSEC3(rdataStart);
            case NSEC3PARAM -> parseNSEC3PARAM(rdataStart);
             */

            default -> {
                error("Unknown record type: " + type, tokenBuffer.getLast().lineNumber());
                yield null;
            }
        };
    }

    private boolean isRRType(ZoneToken t) {
        return ALL_RR_TYPES.contains(t.type());
    }

    private RData parseIPv4(int start) {
        if (start >= tokenBuffer.size()) {
            error("Missing IPv4 address in A record",tokenBuffer.getLast().lineNumber());
            return null;
        }

        ZoneToken addressToken = tokenBuffer.get(start);
        if (addressToken.type() != ZoneTokenType.IPV4_ADDRESS) {
            error("A record requires an IPv4 address", addressToken.lineNumber());
            return null;
        }
        for (int i = start + 1; i < tokenBuffer.size(); i++) {
            warning("trailing garbage in IPv4 record: " + tokenBuffer.get(i).literal(), tokenBuffer.get(i).lineNumber());
        }

        RDataA rdata;
        try {
            byte[] address = InetAddress.getByName(addressToken.literal()).getAddress();
            if (address.length != 4) {
                error("A record requires an IPv4 address", addressToken.lineNumber());
                return null;
            }
            rdata = new RDataA(address);
        } catch (UnknownHostException e) {
            error("Could not read IPv4 address: " + addressToken.literal(), addressToken.lineNumber());
            return null;
        }
        return rdata;
    }

    private RData parseIPv6(int start) {
        if (start >= tokenBuffer.size()) {
            error("Missing IPv6 address in AAAA record", tokenBuffer.getLast().lineNumber());
            return null;
        }

        ZoneToken addressToken = tokenBuffer.get(start);
        if (addressToken.type() != ZoneTokenType.IPV6_ADDRESS) {
            error("AAAA record requires an IPv6 address", addressToken.lineNumber());
            return null;
        }
        for (int i = start + 1; i < tokenBuffer.size(); i++) {
            warning("trailing garbage in IPv6 record: " + tokenBuffer.get(i).literal(), tokenBuffer.get(i).lineNumber());
        }

        RDataAAAA rdata;
        try {
            byte[] address = InetAddress.getByName(addressToken.literal()).getAddress();
            if (address.length != 16) {
                error("AAAA record requires an IPv6 address", addressToken.lineNumber());
                return null;
            }
            rdata = new RDataAAAA(address);
        } catch (UnknownHostException e) {
            error("Could not read IPv6 address: " + addressToken.literal(), addressToken.lineNumber());
            return null;
        }
        return rdata;
    }

    /**
     * Normalizes a domain name using the current origin.
     */
    private RData parseDomainName (RRType type, int start) {
        if (start >= tokenBuffer.size()){
            error("Missing domain name in " + type + " record", tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken nameToken = tokenBuffer.get(start);
        String rawDomainName = normalizeDomainName(nameToken.literal());
        for (int i = start + 1; i < tokenBuffer.size(); i++){
            warning("trailing garbage in " + type + " record: " + tokenBuffer.get(i).literal(), tokenBuffer.get(i).lineNumber());
        }
        String[] domainName = parseDomainLabels(rawDomainName, type + " domain name", nameToken.lineNumber());
        if (parserError) return null;

        RData rdata;
        switch (type) {
            case CNAME -> rdata = new RDataCNAME(domainName);
            case NS -> rdata = new RDataNS(domainName);
            case PTR -> rdata = new RDataPTR(domainName);
            default -> throw new IllegalArgumentException("ILLEGAL: " + type);
        }
        return rdata;
    }

    private RData parseMX(int start) {
        if (start >= tokenBuffer.size()) {
            error("Missing MX preference", tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken prefToken = tokenBuffer.get(start);

        if (prefToken.type() != ZoneTokenType.NUMBER){
            error("MX preference must be an integer", prefToken.lineNumber());
            return null;
        }

        int preference = parseUInt16(prefToken, "MX preference");
        if (parserError) return null;

        int exchangeIndex = start + 1;
        if (exchangeIndex >= tokenBuffer.size()) {
            error("Missing MX exchange", tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken exchangeToken = tokenBuffer.get(exchangeIndex);
        String rawDomainName = normalizeDomainName(exchangeToken.literal());

        for (int i = exchangeIndex + 1; i < tokenBuffer.size(); i++){
            warning("trailing garbage in MX record: " + tokenBuffer.get(i).literal(), tokenBuffer.get(i).lineNumber());
        }
        String[] exchange = parseDomainLabels(rawDomainName, "MX exchange", exchangeToken.lineNumber());
        if (parserError) return null;

        return new RDataMX(preference, exchange);
    }

    private RData parseSOA(int start) {
        int requiredFields = 7;

        if (tokenBuffer.size() - start < requiredFields){
            error("SOA record requires mname, rname, serial, refresh, retry, expire, and minimum", tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken mnameToken = tokenBuffer.get(start);
        ZoneToken rnameToken = tokenBuffer.get(start + 1);

        String rawMName = normalizeDomainName(mnameToken.literal());
        String rawRName = normalizeDomainName(rnameToken.literal());

        long serial  = parseUInt32(tokenBuffer.get(start + 2), "SOA serial");

        long refresh = parseTTL(tokenBuffer.get(start + 3), "SOA refresh");
        long retry   = parseTTL(tokenBuffer.get(start + 4), "SOA retry");
        long expire  = parseTTL(tokenBuffer.get(start + 5), "SOA expire");
        long minimum = parseTTL(tokenBuffer.get(start + 6), "SOA minimum");
        if (parserError) {
            return null;
        }
        for (int i = start + requiredFields; i < tokenBuffer.size(); i++) {
            warning("trailing garbage in SOA record: " + tokenBuffer.get(i).literal(), tokenBuffer.get(i).lineNumber());
        }

        String[] mmName = parseDomainLabels(rawMName, "SOA mname", mnameToken.lineNumber());
        if (parserError) return null;
        String[] rName = parseDomainLabels(rawRName, "SOA rname", rnameToken.lineNumber());
        if (parserError) return null;

        // Historic fallback used if no defaulttl is provided. Idk dnssec used it in my test so why not include it
        if(this.defaultTtl.isEmpty()){
            this.defaultTtl = String.valueOf(minimum);
        }

        return new RDataSOA(mmName, rName, serial, refresh, retry, expire, minimum);
    }

    private RData parseTXT(int start) {
        if (start >= tokenBuffer.size()) {
            error("Missing TXT data", tokenBuffer.getLast().lineNumber());
            return null;
        }
        String[] txt = new String[tokenBuffer.size() - start];

        for (int i = 0; i < txt.length; i++) {
            ZoneToken t = tokenBuffer.get(start + i);
            if (t.type() != ZoneTokenType.TXT){
                error("TXT record contains invalid text", t.lineNumber());
            }
            String value = unescapeText(t.literal(), t.lineNumber());

            if (value == null) {
                return null;
            }

            if (value.getBytes(StandardCharsets.UTF_8).length > 255) {
                error("TXT string too long; max is 255 bytes: " + value, t.lineNumber());
                return null;
            }
            txt[i] = value;
        }
        return new RDataTXT(txt);
    }

    private String unescapeText(String raw, int lineNumber) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (escaped) {
                if (Character.isDigit(c) && i + 2 < raw.length() && Character.isDigit(raw.charAt(i + 1)) && Character.isDigit(raw.charAt(i + 2))) {
                    int value = Integer.parseInt(raw.substring(i, i + 3));
                    if (value > 255) {
                        error("Invalid TXT escape value: \\" + raw.substring(i, i + 3), lineNumber);
                        return null;
                    }
                    out.append((char) value);
                    i += 2;
                } else {
                    out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) {
            error("TXT string ends with unfinished escape", lineNumber);
            return null;
        }
        //IO.println(raw + ":"  + out);
        return out.toString();
    }
    /*
    private RData parseDNSKEY(int start) {
        int requiredFields = 4;
        if (tokenBuffer.size() - start < requiredFields) {
            error("DNSKEY record requires flags, protocol, algorithm, and public key", tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken flagsToken = tokenBuffer.get(start);
        ZoneToken protocolToken = tokenBuffer.get(start + 1);
        ZoneToken algorithmToken = tokenBuffer.get(start + 2);
        ZoneToken publicKeyToken = tokenBuffer.get(start + 3);

        int flags = parseUInt16(flagsToken, "DNSKEY flags");
        int protocol = parseUInt8(protocolToken, "DNSKEY protocol");
        int algorithm = parseUInt8(algorithmToken, "DNSKEY algorithm");

        if (parserError) { return null; }

        if (protocol != 3) {
            error("DNSKEY protocol must be 3", protocolToken.lineNumber());
            return null;
        }

        StringBuilder rawPublicKey = new StringBuilder();

        for (int i = start + 3; i < tokenBuffer.size(); i++) {
            rawPublicKey.append(tokenBuffer.get(i).literal());
        }
        byte[] publicKey;
        try {
            publicKey = Base64.getDecoder().decode(rawPublicKey.toString());
        } catch (IllegalArgumentException e) {
            error("Invalid DNSKEY public key base64", publicKeyToken.lineNumber());
            return null;
        }

        return new RDataDNSKEY(flags, protocol, algorithm, publicKey);
    }
    */

    /*
    private RData parseDS(int start) {
        int requiredFields = 4;

        if (tokenBuffer.size() - start < requiredFields) {
            error("DS record requires key tag, algorithm, digest type, and digest",
                    tokenBuffer.getLast().lineNumber());
            return null;
        }

        ZoneToken keyTagToken = tokenBuffer.get(start);
        ZoneToken algorithmToken = tokenBuffer.get(start + 1);
        ZoneToken digestTypeToken = tokenBuffer.get(start + 2);
        ZoneToken digestToken = tokenBuffer.get(start + 3);

        int keyTag = parseUInt16(keyTagToken, "DS key tag");
        int algorithm = parseUInt8(algorithmToken, "DS algorithm");
        int digestType = parseUInt8(digestTypeToken, "DS digest type");

        if (parserError) {
            return null;
        }

        String rawDigest = digestToken.literal();

        if (!rawDigest.matches("[0-9a-fA-F]+") || rawDigest.length() % 2 != 0) {
            error("Invalid DS digest hex", digestToken.lineNumber());
            return null;
        }

        byte[] digest = hexToBytes(rawDigest);

        for (int i = start + requiredFields; i < tokenBuffer.size(); i++) {
            warning("trailing garbage in DS record: " + tokenBuffer.get(i).literal(),
                    tokenBuffer.get(i).lineNumber());
        }

        return new RDataDS(keyTag, algorithm, digestType, digest);
    }
     */

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];

        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }

        return bytes;
    }

    /*
    private RData parseRRSIG(int start) {
        int requiredFields = 9;
        if (tokenBuffer.size() - start < requiredFields) {
            error("RRSIG record requires type covered, algorithm, labels, original TTL, "
                            + "expiration, inception, key tag, signer name, and signature",
                    tokenBuffer.getLast().lineNumber());
            return null;
        }

        ZoneToken typeCoveredToken = tokenBuffer.get(start);
        ZoneToken algorithmToken = tokenBuffer.get(start + 1);
        ZoneToken labelsToken = tokenBuffer.get(start + 2);
        ZoneToken originalTtlToken = tokenBuffer.get(start + 3);
        ZoneToken expirationToken = tokenBuffer.get(start + 4);
        ZoneToken inceptionToken = tokenBuffer.get(start + 5);
        ZoneToken keyTagToken = tokenBuffer.get(start + 6);
        ZoneToken signerNameToken = tokenBuffer.get(start + 7);

        if (!isRRType(typeCoveredToken)) {
            error("RRSIG type covered must be an RR type", typeCoveredToken.lineNumber());
            return null;
        }

        RRType typeCovered = RRType.valueOf(typeCoveredToken.literal());

        int algorithm = parseUInt8(algorithmToken, "RRSIG algorithm");
        int labels = parseUInt8(labelsToken, "RRSIG labels");
        long originalTtl = parseTTL(originalTtlToken, "RRSIG original TTL");
        long expiration = parseRRSIGTime(expirationToken, "RRSIG expiration");
        long inception = parseRRSIGTime(inceptionToken, "RRSIG inception");
        int keyTag = parseUInt16(keyTagToken, "RRSIG key tag");

        if (parserError) return null;

        String rawSignerName = normalizeDomainName(signerNameToken.literal());
        String[] signerName = parseDomainLabels(rawSignerName, "RRSIG signer name",
                signerNameToken.lineNumber());

        if (parserError) return null;

        StringBuilder rawSignature = new StringBuilder();

        for (int i = start + 8; i < tokenBuffer.size(); i++) {
            rawSignature.append(tokenBuffer.get(i).literal());
        }

        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(rawSignature.toString());
        } catch (IllegalArgumentException e) {
            error("Invalid RRSIG signature base64", tokenBuffer.get(start + 8).lineNumber());
            return null;
        }

        return new RDataRRSIG(typeCovered, algorithm, labels, originalTtl, expiration, inception, keyTag, signerName, signature);
    }
     */

    /*
    private long parseRRSIGTime(ZoneToken t, String fieldName) {
        String literal = t.literal();

        try {
            long value;

            if (literal.length() == 14) {
                LocalDateTime dateTime = LocalDateTime.parse(
                        literal,
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                );

                value = dateTime.toEpochSecond(ZoneOffset.UTC);
            } else {
                value = Long.parseLong(literal);
            }

            if (value < 0 || value > 0xFFFFFFFFL) {
                error(fieldName + " out of range: " + value, t.lineNumber());
                return 0;
            }

            return value;
        } catch (DateTimeParseException | NumberFormatException e) {
            error("Invalid " + fieldName + ": " + literal, t.lineNumber());
            return 0;
        }
    }
     */

    /*
    private RData parseNSEC(int start) {
        int requiredFields = 2;
        if (tokenBuffer.size() - start < requiredFields) {
            error("NSEC record requires next domain name and at least one RR type",
                    tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken nextDomainToken = tokenBuffer.get(start);
        String rawNextDomain = normalizeDomainName(nextDomainToken.literal());
        String[] nextDomain = parseDomainLabels(
                rawNextDomain,
                "NSEC next domain name",
                nextDomainToken.lineNumber()
        );
        if (parserError) return null;

        List<RRType> types = new ArrayList<>();
        for (int i = start + 1; i < tokenBuffer.size(); i++) {
            ZoneToken typeToken = tokenBuffer.get(i);

            if (!isRRType(typeToken)) {
                error("Invalid NSEC RR type: " + typeToken.literal(),
                        typeToken.lineNumber());
                return null;
            }
            types.add(RRType.valueOf(typeToken.literal()));
        }
        byte[] typeBitMaps = buildTypeBitMaps(types);

        return new RDataNSEC(nextDomain, typeBitMaps);
    }
     */

    private byte[] buildTypeBitMaps(List<RRType> types) {
        int maxType = 0;
        for (RRType type : types) {
            maxType = Math.max(maxType, type.getValue());
        }
        int bitmapLength = (maxType / 8) + 1;
        byte[] bitmap = new byte[bitmapLength];

        for (RRType type : types) {
            int value = type.getValue();
            int byteIndex = value / 8;
            int bitIndex = value % 8;
            bitmap[byteIndex] |= (byte) (1 << (7 - bitIndex));
        }
        return bitmap;
    }



    /*
    private RData parseNSEC3(int start) {
        int requiredFields = 5;
        if (tokenBuffer.size() - start < requiredFields) {
            error("NSEC3 record requires hash algorithm, flags, iterations, salt, next hashed owner name, and type bitmaps",
                    tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken hashAlgorithmToken = tokenBuffer.get(start);
        ZoneToken flagsToken = tokenBuffer.get(start + 1);
        ZoneToken iterationsToken = tokenBuffer.get(start + 2);
        ZoneToken saltToken = tokenBuffer.get(start + 3);
        ZoneToken nextHashedOwnerNameToken = tokenBuffer.get(start + 4);

        int hashAlgorithm = parseUInt8(hashAlgorithmToken, "NSEC3 hash algorithm");
        int flags = parseUInt8(flagsToken, "NSEC3 flags");
        int iterations = parseUInt16(iterationsToken, "NSEC3 iterations");

        if (parserError) return null;
        byte[] salt = parseHexOrDash(saltToken, "NSEC3 salt");

        if (parserError) return null;
        byte[] nextHashedOwnerName;

        try {
            nextHashedOwnerName = nextHashedOwnerNameToken.literal().getBytes(StandardCharsets.US_ASCII);        } catch (IllegalArgumentException e) {
            error("Invalid NSEC3 next hashed owner name", nextHashedOwnerNameToken.lineNumber());
            return null;
        }

        List<RRType> types = new ArrayList<>();
        for (int i = start + requiredFields; i < tokenBuffer.size(); i++) {
            ZoneToken typeToken = tokenBuffer.get(i);
            if (!isRRType(typeToken)) {
                error("Invalid NSEC3 RR type: " + typeToken.literal(), typeToken.lineNumber());
                return null;
            }
            types.add(RRType.valueOf(typeToken.literal()));
        }

        byte[] typeBitMaps = buildTypeBitMaps(types);
        return new RDataNSEC3(hashAlgorithm, flags, iterations, salt, nextHashedOwnerName, typeBitMaps);
    }
     */

    /*
    private RData parseNSEC3PARAM(int start) {
        int requiredFields = 4;
        if (tokenBuffer.size() - start < requiredFields) {
            error("NSEC3PARAM record requires hash algorithm, flags, iterations, and salt",
                    tokenBuffer.getLast().lineNumber());
            return null;
        }
        ZoneToken hashAlgorithmToken = tokenBuffer.get(start);
        ZoneToken flagsToken = tokenBuffer.get(start + 1);
        ZoneToken iterationsToken = tokenBuffer.get(start + 2);
        ZoneToken saltToken = tokenBuffer.get(start + 3);

        int hashAlgorithm = parseUInt8(hashAlgorithmToken, "NSEC3PARAM hash algorithm");
        int flags = parseUInt8(flagsToken, "NSEC3PARAM flags");
        int iterations = parseUInt16(iterationsToken, "NSEC3PARAM iterations");

        if (parserError) return null;
        byte[] salt = parseHexOrDash(saltToken, "NSEC3PARAM salt");

        if (parserError) return null;
        for (int i = start + requiredFields; i < tokenBuffer.size(); i++) {
            warning("trailing garbage in NSEC3PARAM record: " + tokenBuffer.get(i).literal(),
                    tokenBuffer.get(i).lineNumber());
        }

        return new RDataNSEC3PARAM(hashAlgorithm, flags, iterations, salt);
    }
     */

    private byte[] parseHexOrDash(ZoneToken token, String fieldName) {
        String literal = token.literal();
        if (literal.equals("-")) {
            return new byte[0];
        }

        if (!literal.matches("[0-9a-fA-F]*") || literal.length() % 2 != 0) {
            error("Invalid " + fieldName + " hex: " + literal, token.lineNumber());
            return new byte[0];
        }
        return hexToBytes(literal);
    }

    private String normalizeDomainName(String name) {
        if (name.equals("@")){ return origin; }
        if (name.endsWith(".")){ return name; }
        return name + "." + origin;
    }

    private int parseUInt8(ZoneToken token, String fieldName) {
        return parseUnsignedInt(token, fieldName, 0, 255);
    }

    private int parseUInt16(ZoneToken token, String fieldName) {
        return parseUnsignedInt(token, fieldName, 0, 65535);
    }

    private int parseUnsignedInt(ZoneToken t, String fieldName, int min, int max) {
        if (t.type() != ZoneTokenType.NUMBER) {
            error(fieldName + " must be an integer", t.lineNumber());
            return 0;
        }

        try {
            int value = Integer.parseInt(t.literal());

            if (value < min || value > max) {
                error(fieldName + " out of range: " + value, t.lineNumber());
                return 0;
            }

            return value;
        } catch (NumberFormatException e) {
            error("Invalid " + fieldName + ": " + t.literal(), t.lineNumber());
            return 0;
        }
    }
    private long parseUInt32(ZoneToken t, String fieldName){
        if (t.type() != ZoneTokenType.NUMBER){
            error(fieldName + " must be an integer", t.lineNumber());
            return 0;
        }
        try {
            long value = Long.parseLong(t.literal());
            if (value < 0 || value > 0xFFFFFFFFL) {
                error(fieldName + " out of range: " + value, t.lineNumber());
                return 0;
            }
            return  value;
        } catch (NumberFormatException e) {
            error("Invalid " + fieldName + ": " + t.literal(), t.lineNumber());
            return 0;
        }
    }

    /**
     * Validates and splits a domain name into DNS labels.
     */
    private String[] parseDomainLabels(String rawName, String context, int lineNumber){
        if( rawName.isEmpty()) {
            error(context + " is empty",lineNumber);
            return new String[0];
        }
        String name = rawName.endsWith(".") ? rawName.substring(0, rawName.length() - 1) : rawName;
        if (name.isEmpty()) {
            return new String[0]; // Root
        }

        List<String> labels = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (escaped) {
                if (Character.isDigit(c)
                        && i + 2 < name.length()
                        && Character.isDigit(name.charAt(i + 1))
                        && Character.isDigit(name.charAt(i + 2))) {
                    int value = Integer.parseInt(name.substring(i, i + 3));
                    if (value > 255) {
                        error(context + " invalid escape value: \\" + name.substring(i, i + 3), lineNumber);
                        return new String[0];
                    }
                    current.append((char) value);
                    i += 2;
                } else {
                    current.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '.') {
                labels.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaped) {
            error(context + " ends with unfinished escape", lineNumber);
            return new String[0];
        }
        labels.add(current.toString());
        int wireLength = 1;
        for (String label : labels) {
            if (label.isEmpty()) {
                error(context + " contains empty label",lineNumber);
                return new String[0];
            }

            int len = label.getBytes(StandardCharsets.UTF_8).length;

            if (len >63) {
                error(context + " label too long: " + label, lineNumber);
                return new String[0];
            }

            wireLength += 1 + len;
        }
        if (wireLength > 255) {
            error(context + " is too long",lineNumber);
            return new String[0];
        }
        return labels.toArray(new String[0]);
    }

    /**
     * Parses a TTL value with optional unit suffix.
     * Supported suffixes are s, m, h, d, and w.
     */
    private long parseTTL(ZoneToken t, String fieldName){
        if (!isTTLToken(t)) {
            error(fieldName + " must be a TTL value", t.lineNumber());
            return 0;
        }
        String literal = t.literal().toLowerCase();

        long multiplier = 1;

        if (literal.endsWith("s")){
            multiplier = 1;
            literal = literal.substring(0, literal.length() - 1);
        } else if (literal.endsWith("m")) {
            multiplier = 60;
            literal = literal.substring(0, literal.length() - 1);
        } else if (literal.endsWith("h")) {
            multiplier = 3600;
            literal = literal.substring(0, literal.length() - 1);
        } else if (literal.endsWith("d")) {
            multiplier = 86400;
            literal = literal.substring(0, literal.length() - 1);
        } else if (literal.endsWith("w")) {
            multiplier = 604800;
            literal = literal.substring(0, literal.length() - 1);
        }

        try {
            long base = Long.parseLong(literal);
            long result = Math.multiplyExact(base, multiplier);
            if (result < 0 || result > 0xFFFFFFFFL) {
                error(fieldName + " out of range: " + result, t.lineNumber());
                return 0;
            }
            return result;
        } catch (NumberFormatException e) {
            error("Invalid " + fieldName + ": " + t.literal(), t.lineNumber());
            return 0;
        } catch (ArithmeticException e) {
            error(fieldName + " overflow: " + t.literal(), t.lineNumber());
            return 0;
        }
    }

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String BOLD   = "\u001B[1m";

    /**
     * Reports a fatal zone parsing error and marks the parser as failed.
     */
    private void error(String message, int lineNumber) {
        System.err.println(RED + BOLD + "[ZONE-ERROR]" + RESET + RED + " line " + RESET + YELLOW + lineNumber + RESET + RED + ": " + message + RESET);

        parserError = true;
    }

    /**
     * Reports a non-fatal zone parsing warning.
     */
    private void warning(String message, int lineNumber) {
        System.err.println(YELLOW + BOLD + "[ZONE-WARNING]" + RESET + YELLOW + " line "  + RESET + CYAN + lineNumber + RESET + YELLOW + ": " + message + RESET);
    }
    public boolean hasParserError(){
        return parserError;
    }
}
