package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.*;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.gitlab.teamproject2026.logging.LoggingFeature;
import io.gitlab.teamproject2026.logging.DNSEventInfo;
import io.gitlab.teamproject2026.logging.DNSEventDebug;
import io.gitlab.teamproject2026.logging.DNSEventType;


public class StubResolver {
    public enum Status{SUCCESS, TIMEOUT, IO_ERROR}
    public enum Strategy{FAILOVER, ROUND_ROBIN}
    public record ForwardResult(Status status, byte[] response, String errorMessage, long latencyMs){
        //public boolean isSuccess() {return status== Status.SUCCESS;}
    }
    //host should be ip, name can be additional info
    public record NameServer(String host, int port, String name) {
        @Override public String toString() {return name+ "(" + host+ ":" +port+ ")";}
    }
    public static final int MAX_UDP_SIZE=4096;

    private final List<NameServer> nameServers;
    private final int timeoutMs;
    private final int maxRetries;
    private final long retryDelay;
    private final Strategy strategy;
    private int RRindex=0;

    public StubResolver(List<NameServer> nameServers, int timeoutMs, int maxRetries, long retryDelay, Strategy strategy){
        if(nameServers==null || nameServers.isEmpty())
            throw new IllegalArgumentException("at least one name server is required");
        this.nameServers=List.copyOf(nameServers);
        this.timeoutMs=timeoutMs;
        this.maxRetries=maxRetries;
        this.retryDelay=retryDelay;
        this.strategy=strategy;
    }

    /**
     * Attempts to forward the query to an upstream server, 
     * retrying according to the configured strategy.
     * Returns SERVFAIL if all attempts fail.
     * @param query
     * @return response
     */
    public byte[] resolve(byte[] query){

        int totalServers =nameServers.size();
        int maxAttempts= maxRetries+1;
        int startIndex= nextStartIndex(totalServers);
        long startTime = System.currentTimeMillis();
        //parse query
        //DNSPacket queryPacket = DNSPacket.fromBytes(query);
        DNSPacket queryPacket;
        try {
            queryPacket = DNSPacket.fromBytes(query);
        } catch (Exception e) {
            return buildServFail(query);
        }
        for(int attempt=0; attempt<maxAttempts;attempt++){
            NameServer server=nameServers.get(startIndex);
            //log QUERY_SENT for every question section seperately (usually different qType or domain queried)
            logQuestionEvents(DNSEventType.QUERY_SENT, queryPacket, 0L, server, attempt, null, null);
            IO.println("[Stub] attempt "+ (attempt+1)+ "/" + maxAttempts+ "=>" +server);

            ForwardResult result= forwardOnce(query, server);
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
                    //DNSPacket responsePacket = DNSPacket.fromBytes(result.response());
                    DNSPacket responsePacket;
                    try {
                        responsePacket = DNSPacket.fromBytes(result.response());
                    } catch (Exception e) {
                        logQuestionEvents(DNSEventType.IO_ERROR, queryPacket,
                            result.latencyMs(), server, attempt,
                            "invalid DNS response", null);
                        break; // retry 
                    }
                    RCode rCode = responsePacket.getRcode();
                    //log SUCCESS for every question section seperately
                    logQuestionEvents(DNSEventType.SUCCESS, responsePacket, result.latencyMs(), server, attempt, result.errorMessage(), rCode);
                    IO.println("[Stub] reply from " + server + " in " + result.latencyMs() + "ms");
                    return result.response();
                }
                case TIMEOUT -> {
                    //  TIMEOUT Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "timed out";
                    //log TIMEOUT for every question section seperately
                    logQuestionEvents(DNSEventType.TIMEOUT, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    IO.println("[Stub] timeout from " + server + " after " + result.latencyMs() + " ms");
                    if (attempt + 1 < maxAttempts) {
                        if (strategy == Strategy.FAILOVER) {
                            IO.println("[Stub] switching to next available nameserver..");
                            startIndex = (startIndex + 1) % totalServers;
                        } else {
                            IO.println("[Stub] retrying current configuration target..");
                        }
                        sleep(retryDelay);
                    }
                }
                case IO_ERROR -> {
                    //IO_ERROR Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "I/O error";
                    //log IO_ERROR for every question section seperately
                    logQuestionEvents(DNSEventType.IO_ERROR, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    IO.println("[Stub] I/O error from " + server + " after: " + result.errorMessage());
                    if (attempt + 1 < maxAttempts) {
                        if (strategy == Strategy.FAILOVER) {
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
            nameServers.get(startIndex),
            maxAttempts - 1, //-1 because of "attempt+1" in def of logQuestionEvents
            "All structural retries exhausted. Returning SERVFAIL.",
            RCode.SERVFAIL // buildServFail sets RCode to SERVFAIL
        );

        IO.println("[Stub] All "+maxAttempts+ " attempt(s) exhausted - returning SERVFAIL");
        return buildServFail(query);
    }

    /**
     *  Sends the query to a single server via a new UDP socket and returns the result,
     *  verifying the response source IP and transaction ID.
     * @param query 
     * @param server
     * @return
     */
    private ForwardResult forwardOnce(byte[] query, NameServer server){
        long start= System.currentTimeMillis();
        try(DatagramSocket socket= new DatagramSocket()){socket.setSoTimeout(timeoutMs);

            InetAddress address= InetAddress.getByName(server.host());
            socket.send(new DatagramPacket(query, query.length,address, server.port()));

            byte[] buffer = new byte[MAX_UDP_SIZE];
            DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inPacket);

            byte[] response= Arrays.copyOf(inPacket.getData(), inPacket.getLength());
            if (!inPacket.getAddress().equals(address))
                return new ForwardResult(Status.IO_ERROR, null, "response from unexpected source", System.currentTimeMillis()- start);
            if (response.length < 12)
                return new ForwardResult(Status.IO_ERROR, null, "response too short", System.currentTimeMillis() - start);
            if (response[0] != query[0] || response[1] != query[1])
                return new ForwardResult(Status.IO_ERROR, null, "transaction ID mismatch", System.currentTimeMillis() - start);
            return new ForwardResult(Status.SUCCESS, response, null,System.currentTimeMillis()- start);
        } catch (SocketTimeoutException e){
            return new ForwardResult(Status.TIMEOUT, null, "timed out",System.currentTimeMillis()- start);
        } catch (Exception e){
            return new ForwardResult(Status.IO_ERROR, null, e.getMessage(),System.currentTimeMillis()- start);
        }
    }

    /**
     * Returns the index of the next server to try,
     * rotating for ROUND_ROBIN or always returning 0 for FAILOVER.
     * @param total
     * @return
     */
    private synchronized int nextStartIndex(int total){
        if(strategy== Strategy.ROUND_ROBIN){
            int index=RRindex;
            RRindex=(RRindex+1)%total;
            return index;
        }
        return 0;
    }

    /**
     * Constructs a SERVFAIL DNS response from the original query, used when all upstream attempts are exhausted.
     * Also clears all answer/authority/additional record counts
     * @param query
     * @return
     */
    static byte[] buildServFail(byte[] query){
        if (query.length < 12)
            throw new IllegalArgumentException("query too short to build a valid DNS response");
        byte[] r= Arrays.copyOf(query, query.length);
        r[2]=(byte) ((r[2] & 0x01) | 0x80); 
        // r[2]: QR=1 (response), Opcode=0, AA=0 (not authoritative), TC=0 (no upstream response to copy from), RD=preserved from query
        r[3]=(byte) 0x02; // set RA (0x82) once recursion is available
        // r[3]: RA=0 (no recursion implemented yet), Z=0 (reserved 0), AD=0 (no DNSSEC validation), CD=0 (DNSSEC client flag so irrelevant for response), RCODE=2 (SERVFAIL)
        // since this is a failure response there are no records to return, so the rest is 0 
        r[6]=0;
        r[7]=0;
        r[8]=0;
        r[9]=0;
        r[10]=0;
        r[11]=0;
        return r;
    }

    /**
     * Pauses execution for the given duration plus a small random jitter to avoid synchronized retries.
     * @param ms
     */
    private static void sleep(long ms){
        if (ms <= 0) return;
        try{
            long jitter=ThreadLocalRandom.current().nextInt(30);
            Thread.sleep(ms+jitter);
        }
        catch(InterruptedException e) {Thread.currentThread().interrupt();}
    }

    /**
     * helper method for logging,
     * logs event (TIMEOUT, SUCCESS, IO_ERROR) for every  questionSection seperately
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
            if (type != DNSEventType.TIMEOUT && type != DNSEventType.IO_ERROR) {
                LoggingFeature.logInfo(DNSEventInfo.info(type, id, domain, qType, latency));
            }
            //log debug
            LoggingFeature.logDebug(DNSEventDebug.debug(
                type, id, domain, qType, latency,
                this.strategy.name(), server.name(), server.host(), attempt + 1, error, rCode
            ));
        }
    }
}
