package io.gitlab.teamproject2026.logging;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.fields;

public class LoggingFeature {

    private static final Logger log = LoggerFactory.getLogger("DNS");

    public static boolean isDebug() {
        return log.isDebugEnabled();
    }//set in lobgack.xml for root

    public static void logInfo(DNSEventInfo event) {
        log.info("Summary", fields(event));
    }

    public static void logDebug(DNSEventDebug event) {
        log.debug("Detailed", fields(event));
    }
}