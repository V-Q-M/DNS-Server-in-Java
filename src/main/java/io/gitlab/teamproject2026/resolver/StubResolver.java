package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.logging.*;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;


import io.gitlab.teamproject2026.logging.DNSEvent;


public class StubResolver extends AbstractResolver{

    private final List<NameServer> forwarders;
    private final int maxRetries;
    private final StubResolverStrategy strategy;
    private int RRIndex =0;
    public static Logger logger;


    public StubResolver(List<NameServer> forwarders, int timeoutMs, int maxRetries, long retryDelay, StubResolverStrategy strategy){
        if(forwarders==null || forwarders.isEmpty())
            throw new IllegalArgumentException("at least one name server is required");
        this.forwarders=List.copyOf(forwarders);
        this.timeoutMs=timeoutMs;
        this.maxRetries=maxRetries;
        this.retryDelay=retryDelay;
        this.strategy=strategy;
    }

    /**
     * Resolves a DNS query by forwarding it to one or more upstream servers
     * according to the configured retry and server selection strategy.
     * Falls back to returning a SERVFAIL response if all attempts fail.
     *
     * @param query raw DNS query packet
     * @return raw DNS response packet from an upstream server, or a SERVFAIL response
     */
    @Override
    public byte[] resolve(byte[] query){

        int totalServers = forwarders.size();
        int maxAttempts = maxRetries+1;
        int startIndex = nextStartIndex(totalServers);
        long startTime = System.currentTimeMillis();
        //parse query
        DNSPacket queryPacket;
        try {
            queryPacket = DNSPacket.fromBytes(query);
        } catch (Exception e) {
            return buildServFail(query);
        }
        for(int attempt=0; attempt<maxAttempts;attempt++){
            NameServer server = forwarders.get(startIndex);
            //log QUERY_SENT for every question section separately (usually different qType or domain queried)
            logger.log(
                    ("[STUB] Attempt: " + (attempt + 1) + "/" + maxAttempts + ". Sending query to: " + server),
                    LoggingCategoryType.GENERAL,
                    LoggingSeverity.NOTICE,
                    true
            );
            logQuestionEvents(DNSEventType.QUERY_SENT, queryPacket, 0L, server, attempt, null, null);
            //IO.println("[STUB] Attempt: " + (attempt + 1) + "/" + maxAttempts + ". Sending query to: " + server);

            QueryResult result = queryUDP(query, server, timeoutMs);//forward once over UDP

            //for all questions
            switch (result.status()) {
                case SUCCESS -> {
                    // SUCCESS Event (only means that an answer was rcvd,
                    // not necessarily answer to query,
                    // different RCodes (NXDOMAIN,NO_ERROR,...) indicate if rcvd answer actually resolves query)
                    if (result.response() == null || result.response().length < 12) {
                        logQuestionEvents(DNSEventType.IO_ERROR, queryPacket,
                                result.latencyMs(), server, attempt,
                                "invalid DNS response", null);
                        break; // retry
                    }

                    if (isTruncated(result.response())){
                        logger.log(
                                (YELLOW + "[STUB] UDP response truncated. Retrying over TCP..." + RESET),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.NOTICE,
                                true
                        );
                        //IO.println(YELLOW + "[STUB] UDP response truncated. Retrying over TCP..." + RESET);
                        QueryResult tcpResult = queryTCP(query, server, timeoutMs); //forward once over TCP

                        if (tcpResult.status() != Status.SUCCESS){
                            logger.log(
                                    (RED + "[STUB] TCP fallback failed: "
                                            + tcpResult.status()
                                            + " - "
                                            + tcpResult.errorMessage()
                                            + RESET),
                                    LoggingCategoryType.GENERAL,
                                    LoggingSeverity.NOTICE,
                                    true
                            );
                            //IO.println(RED + "[STUB] TCP fallback failed: "
                                 //   + tcpResult.status()
                                  //  + " - "
                                   // + tcpResult.errorMessage()
                                   // + RESET);

                            logQuestionEvents(
                                    tcpResult.status() == Status.TIMEOUT ? DNSEventType.TIMEOUT : DNSEventType.IO_ERROR,
                                    queryPacket,
                                    tcpResult.latencyMs(),
                                    server,
                                    attempt,
                                    tcpResult.errorMessage(),
                                    null
                            );
                            break;
                        }
                        result = tcpResult;
                    }

                    DNSPacket responsePacket;
                    try {
                        responsePacket = DNSPacket.fromBytes(result.response());
                    } catch (Exception e) {
                        logger.log(
                                (RED + "[STUB] Invalid DNS response: " + e.getMessage() + RESET),
                                LoggingCategoryType.GENERAL,
                                LoggingSeverity.NOTICE,
                                true
                        );
                        //IO.println(RED + "[STUB] Invalid DNS response: " + e.getMessage() + RESET);
                        
                        logQuestionEvents(DNSEventType.IO_ERROR, queryPacket,
                            result.latencyMs(), server, attempt,
                            "invalid DNS response", null);
                        break; // retry 
                    }
                    RCode rCode = responsePacket.getRcode();
                    //log SUCCESS for every question section separately
                    logQuestionEvents(DNSEventType.SUCCESS, responsePacket, result.latencyMs(), server, attempt, result.errorMessage(), rCode);
                    logger.log(
                            (GREEN + "[STUB] Success! Reply from: " + server + " took " + result.latencyMs() + "ms" + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true
                    );
                    //IO.println(GREEN + "[STUB] Success! Reply from: " + server + " took " + result.latencyMs() + "ms" + RESET);
                    return result.response();
                }
                case TIMEOUT -> {
                    //  TIMEOUT Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "timed out";
                    //log TIMEOUT for every question section separately
                    logQuestionEvents(DNSEventType.TIMEOUT, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    logger.log(
                            (YELLOW + "[STUB] Timeout from: " + server + " after " + result.latencyMs() + " ms" + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true
                    );
                    //IO.println(YELLOW + "[STUB] Timeout from: " + server + " after " + result.latencyMs() + " ms" + RESET);
                    if (attempt + 1 < maxAttempts) {
                        if (strategy == StubResolverStrategy.FAILOVER) {
                            logger.log(
                                    ("[STUB] Switching to next available nameserver.."),
                                    LoggingCategoryType.GENERAL,
                                    LoggingSeverity.NOTICE,
                                    true
                            );
                            //IO.println("[STUB] Switching to next available nameserver..");
                            startIndex = (startIndex + 1) % totalServers;
                        } else {
                            logger.log(
                                    ("[STUB] Retrying current configuration target.."),
                                    LoggingCategoryType.GENERAL,
                                    LoggingSeverity.NOTICE,
                                    true
                            );
                            //IO.println("[STUB] Retrying current configuration target..");
                        }
                        sleep(retryDelay);
                    }
                }
                case IO_ERROR -> {
                    //IO_ERROR Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "I/O error";
                    //log IO_ERROR for every question section separately
                    logQuestionEvents(DNSEventType.IO_ERROR, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    logger.log(
                            (RED + "[STUB] I/O error from: " + server + " after: " + result.errorMessage() + RESET),
                            LoggingCategoryType.GENERAL,
                            LoggingSeverity.NOTICE,
                            true
                    );
                    //IO.println(RED + "[STUB] I/O error from: " + server + " after: " + result.errorMessage() + RESET);
                    if (attempt + 1 < maxAttempts) {
                        if (strategy == StubResolverStrategy.FAILOVER) {
                            startIndex = (startIndex + 1) % totalServers;
                        }
                        sleep(retryDelay);
                    }
                }
            }}
        // exhausted  attempts failure Event
        logQuestionEvents(
            DNSEventType.ATTEMPTS_EXHAUSTED,
            queryPacket,
            System.currentTimeMillis() - startTime,
            forwarders.get(startIndex),
            maxAttempts - 1, //-1 because of "attempt+1" in def of logQuestionEvents
            "All structural retries exhausted. Returning SERVFAIL.",
            RCode.SERVFAIL // buildServFail sets RCode to SERVFAIL
        );
        logger.log(
                (RED + "[STUB] All "+maxAttempts+ " attempt(s) exhausted -> returning SERVFAIL" + RESET),
                LoggingCategoryType.GENERAL,
                LoggingSeverity.NOTICE,
                true
        );
        //IO.println(RED + "[STUB] All "+maxAttempts+ " attempt(s) exhausted -> returning SERVFAIL" + RESET);
        return buildServFail(query);
    }

    /**
     * Determines the starting forwarder index for the current resolution attempt.
     * In ROUND_ROBIN mode the index advances between requests, whereas FAILOVER
     * mode always starts with the first configured server.
     *
     * @param total number of configured forwarders
     * @return index of the forwarder to try first
     */
    private synchronized int nextStartIndex(int total){
        if(strategy== StubResolverStrategy.ROUND_ROBIN){
            int index= RRIndex;
            RRIndex =(RRIndex +1)%total;
            return index;
        }
        return 0;
    }






    /**
     * helper method for logging,
     * logs event (TIMEOUT, SUCCESS, IO_ERROR) for every  questionSection separately
     * (possibly different queried domains and qTypes in every questionSection, so for dashboard statistics probably relevant)
     * @params : all input needed by logDebug
     */
    private void logQuestionEvents(DNSEventType type, DNSPacket packet, long latency, NameServer server, int attempt, String error, RCode rCode) {
        byte[] idBytes = packet.getId();
        String id = "0x" + java.util.HexFormat.of().formatHex(idBytes);
        if (packet.getQdcount() == 0 || packet.getQuestionSections() == null ) {
            return; 
        }
        for (QuestionSection question : packet.getQuestionSections()) {
            String domain = question.nameToString();
            RRType qType = question.getQtype();
            //log info
            if (type == DNSEventType.QUERY_SENT || type == DNSEventType.SUCCESS) {
                logger.log(
                        (DNSEvent.event(type, id, domain, qType, latency, ResponseSource.FORWARDER,
                                this.strategy.name(), server.name(), server.host(), attempt + 1, error, rCode).toHumanReadable()),
                        LoggingCategoryType.QUERIES,
                        LoggingSeverity.INFO
                );
             //   LoggingFeature.logInfo(DNSEventInfo.info(type, id, domain, qType, latency, ResponseSource.FORWARDER));
            }
            //log debug
            else {
                logger.log(
                        (DNSEvent.event(type, id, domain, qType, latency, ResponseSource.FORWARDER,
                                this.strategy.name(), server.name(), server.host(), attempt + 1, error, rCode).toHumanReadable()),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR
                );
            }
            //LoggingFeature.logDebug(DNSEventDebug.debug(
            //        type, id, domain, qType, latency, ResponseSource.FORWARDER,
             //       this.strategy.name(), server.name(), server.host(), attempt + 1, error, rCode
            //));

        }
    }
}
