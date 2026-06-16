package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.gitlab.teamproject2026.logging.LoggingFeature;
import io.gitlab.teamproject2026.logging.DNSEventInfo;
import io.gitlab.teamproject2026.logging.DNSEventDebug;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.ResponseSource;


public class StubResolver {
    public enum Status{SUCCESS, TIMEOUT, IO_ERROR}
    public record ForwardResult(Status status, byte[] response, String errorMessage, long latencyMs){
        //public boolean isSuccess() {return status== Status.SUCCESS;}
    }
    public static final int MAX_UDP_SIZE=4096;

    private final List<Forwarder> forwarders;
    private final int timeoutMs;
    private final int maxRetries;
    private final long retryDelay;
    private final StubResolverStrategy strategy;
    private int RRIndex =0;

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN   = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";

    public StubResolver(List<Forwarder> forwarders, int timeoutMs, int maxRetries, long retryDelay, StubResolverStrategy strategy){
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
            Forwarder server = forwarders.get(startIndex);
            //log QUERY_SENT for every question section separately (usually different qType or domain queried)
            logQuestionEvents(DNSEventType.QUERY_SENT, queryPacket, 0L, server, attempt, null, null);
            IO.println("[STUB] Attempt: " + (attempt + 1) + "/" + maxAttempts + ". Sending query to: " + server);

            ForwardResult result = forwardOnceUDP(query, server);

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

                    if (resultIsTruncated(result.response())){
                        IO.println(YELLOW + "[STUB] UDP response truncated. Retrying over TCP..." + RESET);
                        ForwardResult tcpResult = forwardOnceTCP(query, server);

                        if (tcpResult.status() != Status.SUCCESS){
                            IO.println(RED + "[STUB] TCP fallback failed: "
                                    + tcpResult.status()
                                    + " - "
                                    + tcpResult.errorMessage()
                                    + RESET);

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

                    //DNSPacket responsePacket = DNSPacket.fromBytes(result.response());
                    DNSPacket responsePacket;
                    try {
                        responsePacket = DNSPacket.fromBytes(result.response());
                    } catch (Exception e) {
                        IO.println(RED + "[STUB] Invalid DNS response: " + e.getMessage() + RESET);
                        
                        logQuestionEvents(DNSEventType.IO_ERROR, queryPacket,
                            result.latencyMs(), server, attempt,
                            "invalid DNS response", null);
                        break; // retry 
                    }
                    RCode rCode = responsePacket.getRcode();
                    //log SUCCESS for every question section separately
                    logQuestionEvents(DNSEventType.SUCCESS, responsePacket, result.latencyMs(), server, attempt, result.errorMessage(), rCode);
                    IO.println(GREEN + "[STUB] Success! Reply from: " + server + " took " + result.latencyMs() + "ms" + RESET);
                    return result.response();
                }
                case TIMEOUT -> {
                    //  TIMEOUT Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "timed out";
                    //log TIMEOUT for every question section separately
                    logQuestionEvents(DNSEventType.TIMEOUT, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    IO.println(YELLOW + "[STUB] Timeout from: " + server + " after " + result.latencyMs() + " ms" + RESET);
                    if (attempt + 1 < maxAttempts) {
                        if (strategy == StubResolverStrategy.FAILOVER) {
                            IO.println("[STUB] Switching to next available nameserver..");
                            startIndex = (startIndex + 1) % totalServers;
                        } else {
                            IO.println("[STUB] Retrying current configuration target..");
                        }
                        sleep(retryDelay);
                    }
                }
                case IO_ERROR -> {
                    //IO_ERROR Event
                    String error = result.errorMessage() != null ? result.errorMessage() : "I/O error";
                    //log IO_ERROR for every question section separately
                    logQuestionEvents(DNSEventType.IO_ERROR, queryPacket, result.latencyMs(), server, attempt, error, null); // RCode is null since response is null
                    IO.println(RED + "[STUB] I/O error from: " + server + " after: " + result.errorMessage() + RESET);
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

        IO.println(RED + "[STUB] All "+maxAttempts+ " attempt(s) exhausted -> returning SERVFAIL" + RESET);
        return buildServFail(query);
    }

    /**
     * Sends the query to a single upstream server over UDP and waits for a response.
     * The received packet is validated against the expected source address and
     * transaction ID before being returned.
     *
     * @param query raw DNS query packet
     * @param server upstream DNS server to contact
     * @return the forwarding result including status, response, and latency
     */
    private ForwardResult forwardOnceUDP(byte[] query, Forwarder server){
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
     * Sends the query to a single upstream server over TCP.
     * Used as a fallback when a UDP response is truncated.
     *
     * @param query raw DNS query packet
     * @param server upstream DNS server to contact
     * @return the forwarding result including status, response, and latency
     */
    private ForwardResult forwardOnceTCP(byte[] query, Forwarder server){
        long start = System.currentTimeMillis();
        try (Socket socket =  new Socket()) {
            socket.connect(new InetSocketAddress(server.host(), server.port()), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            out.writeShort(query.length);
            out.write(query);
            out.flush();

            int responseLength = in.readUnsignedShort();
            if (responseLength < 12){
                return  new ForwardResult(Status.IO_ERROR, null, "response too short", System.currentTimeMillis() - start);
            }

            byte[] response = in.readNBytes(responseLength);

            if (responseLength != response.length){
                return new ForwardResult( Status.IO_ERROR, null, "incomplete TCP response", System.currentTimeMillis() - start );
            }

            if (response[0] != query[0] || response[1] != query[1]) {
                return new ForwardResult( Status.IO_ERROR, null, "transaction ID mismatch", System.currentTimeMillis() - start );
            }

            return  new ForwardResult(Status.SUCCESS, response, null, System.currentTimeMillis() - start);

        } catch (SocketTimeoutException e){
            return new ForwardResult( Status.TIMEOUT, null, "timed out", System.currentTimeMillis() - start );
        } catch (Exception e){
            return new ForwardResult( Status.IO_ERROR, null, e.getMessage(), System.currentTimeMillis() - start );
        }
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
     * Constructs a SERVFAIL DNS response from the original query, used when all upstream attempts are exhausted.
     * Also clears all answer/authority/additional record counts
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
     * Checks whether a DNS response indicates truncation and therefore
     * requires retrying the query over TCP.
     *
     * @param response raw DNS response packet
     * @return {@code true} if the TC flag is set, otherwise {@code false}
     */
    private boolean resultIsTruncated(byte[] response){
        return response.length >= 4 && ((response[2] & 0x02) != 0);
    }

    /**
     * Sleeps for the specified duration before another retry attempt.
     * A small random jitter is added to avoid synchronized retry storms
     * when multiple resolvers retry simultaneously.
     *
     * @param ms base delay in milliseconds
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
     * logs event (TIMEOUT, SUCCESS, IO_ERROR) for every  questionSection separately
     * (possibly different queried domains and qTypes in every questionSection, so for dashboard statistics probably relevant)
     * @params : all input needed by logDebug
     */
    private void logQuestionEvents(DNSEventType type, DNSPacket packet, long latency, Forwarder server, int attempt, String error, RCode rCode) {
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
                LoggingFeature.logInfo(DNSEventInfo.info(type, id, domain, qType, latency, ResponseSource.FORWARDER));
            }
            //log debug
            LoggingFeature.logDebug(DNSEventDebug.debug(
                    type, id, domain, qType, latency, ResponseSource.FORWARDER,
                    this.strategy.name(), server.name(), server.host(), attempt + 1, error, rCode
            ));
        }
    }
}
