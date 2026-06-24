package io.gitlab.teamproject2026.resolver;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractResolver {

    public enum Status { SUCCESS, TIMEOUT, IO_ERROR }

    public record QueryResult( Status status,
                               byte[] response,
                               String errorMessage,
                               long latencyMs) {}

    public static final int MAX_UDP_SIZE = 4096;

    public abstract byte[] resolve(byte[] query) throws Exception;

    /**
     * Sends a DNS query to a single nameserver/forwarder over UDP.
     * The received packet is validated against the expected source address and
     * transaction ID before being returned.
     *
     * @param query     raw DNS query bytes
     * @param server    target nameserver
     * @param timeoutMs socket timeout in milliseconds
     * @return QueryResult including status, response bytes, and latency
     */

    public QueryResult queryUDP(byte[] query, NameServer server, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (DatagramSocket socket = new DatagramSocket()) {socket.setSoTimeout(timeoutMs);

            InetAddress address = InetAddress.getByName(server.host());
            socket.send(new DatagramPacket(query, query.length, address, server.port()));

            byte[] buffer = new byte[MAX_UDP_SIZE];
            DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inPacket);

            byte[] response = Arrays.copyOf(inPacket.getData(), inPacket.getLength());

            if (!inPacket.getAddress().equals(address))
                return new QueryResult(Status.IO_ERROR, null, "response from unexpected source",System.currentTimeMillis() - start);
            if (response.length < 12)
                return new QueryResult(Status.IO_ERROR, null, "response too short",System.currentTimeMillis() - start);
            if (response[0] != query[0] || response[1] != query[1])
                return new QueryResult(Status.IO_ERROR, null, "transaction ID mismatch",System.currentTimeMillis() - start);

            return new QueryResult(Status.SUCCESS, response, null, System.currentTimeMillis() - start);

        } catch (SocketTimeoutException e) {
            return new QueryResult(Status.TIMEOUT, null, "timed out", System.currentTimeMillis() - start);
        } catch (IOException e) {
            return new QueryResult(Status.IO_ERROR, null, e.getMessage(), System.currentTimeMillis() - start);
        }
    }


    /**
     * Sends a query to nameserver/forwarder over TCP.
     * Used as fallback when a UDP response is truncated.
     *
     * @param query     raw DNS query bytes
     * @param server    target nameserver
     * @param timeoutMs socket timeout in milliseconds
     * @return QueryResult including status, response bytes, and latency
     */
    public QueryResult queryTCP(byte[] query, NameServer server, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {socket.connect(new InetSocketAddress(server.host(), server.port()), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            out.writeShort(query.length);
            out.write(query);
            out.flush();

            int responseLength = in.readUnsignedShort();
            if (responseLength < 12)
                return new QueryResult(Status.IO_ERROR, null, "response too short",System.currentTimeMillis() - start);

            byte[] response = in.readNBytes(responseLength);
            if (response.length != responseLength)
                return new QueryResult(Status.IO_ERROR, null, "incomplete TCP response",System.currentTimeMillis() - start);
            if (response[0] != query[0] || response[1] != query[1])
                return new QueryResult(Status.IO_ERROR, null, "transaction ID mismatch",System.currentTimeMillis() - start);

            return new QueryResult(Status.SUCCESS, response, null, System.currentTimeMillis() - start);

        } catch (SocketTimeoutException e) {
            return new QueryResult(Status.TIMEOUT, null, "timed out",  System.currentTimeMillis() - start);
        } catch (IOException e) {
            return new QueryResult(Status.IO_ERROR, null, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Checks the TC (truncation) bit in DNS response header.
     *
     * @param response raw DNS response bytes
     * @return true if the TC flag is set
     */
    public boolean isTruncated(byte[] response) { return response.length >= 4 && (response[2] & 0x02) != 0;}

    /**
     * Constructs a SERVFAIL DNS response from the original query, used when all upstream attempts are exhausted.
     * Also clears all answer/authority/additional record counts
     *
     * @param query DNS query bytes
     * @return SERVFAIL response bytes
     */
    public static byte[] buildServFail(byte[] query){
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
     * Sleeps for the specified duration before another retry attempt.
     * A small random jitter is added to avoid synchronized retry storms
     * when multiple resolvers retry simultaneously.
     *
     * @param ms base delay in milliseconds
     */
    public static void sleep(long ms){
        if (ms <= 0) return;
        try{
            long jitter= ThreadLocalRandom.current().nextInt(30);
            Thread.sleep(ms+jitter);
        }
        catch(InterruptedException e) {Thread.currentThread().interrupt();}
    }

}
