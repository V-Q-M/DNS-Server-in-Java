package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.model.RCode;

import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
public class StubResolver {
    public enum Status{SUCCESS, TIMEOUT, IO_ERROR}
    public enum Strategy{FAILOVER, ROUND_ROBIN}
    public record ForwardResult(Status status, byte[] response, String errorMessage, long latencyMs){
        //public boolean isSuccess() {return status== Status.SUCCESS;}
    }
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

    public byte[] resolve(byte[] query){
        int totalServers =nameServers.size();
        int maxAttempts= maxRetries+1;
        int startIndex= nextStartIndex(totalServers);
        for(int attempt=0; attempt<maxAttempts;attempt++){
            NameServer server=nameServers.get(startIndex);
            IO.println("[Stub] attempt "+ (attempt+1)+ "/" + maxAttempts+ "=>" +server);
            ForwardResult result= forwardOnce(query, server);
            switch (result.status()) {
                case SUCCESS -> {IO.println("[Stub] reply from "+ server+ "in"+ result.latencyMs()+"ms");
                    return result.response();
                }
                case TIMEOUT -> {IO.println("[Stub] timeout from "+server+" after "+result.latencyMs()+ " ms");
                    if (attempt+1 < maxAttempts){
                        startIndex=(startIndex+1)%totalServers;
                        if(strategy==Strategy.FAILOVER){
                            IO.println("[Stub] switching to next available nameserver..");
                        } else{
                            IO.println("[Stub] retrying current configuration target..");
                        }
                        sleep(retryDelay);
                    }}
                case IO_ERROR -> {IO.println("[Stub] I/O error from "+server+ " after "
                    + ": "+ result.errorMessage());
                  if(attempt+1 < maxAttempts){
                      startIndex=(startIndex+1)%totalServers;
                      sleep(retryDelay);
                  }
            }
        }}
        IO.println("[Stub] All "+maxAttempts+ " attemp(s) exhausted - returning SERVFAIL");
        return buildServFail(query);
    }

    private ForwardResult forwardOnce(byte[] query, NameServer server){
        long start= System.currentTimeMillis();
        try(DatagramSocket socket= new DatagramSocket()){socket.setSoTimeout(timeoutMs);

            InetAddress address= InetAddress.getByName(server.host());
            socket.send(new DatagramPacket(query, query.length,address, server.port()));
            byte[] buffer = new byte[MAX_UDP_SIZE];
            DatagramPacket inPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inPacket);
            byte[] response= Arrays.copyOf(inPacket.getData(), inPacket.getLength());
            return new ForwardResult(Status.SUCCESS, response, null,System.currentTimeMillis()- start);
        } catch (SocketTimeoutException e){
            return new ForwardResult(Status.TIMEOUT, null, "timed out",System.currentTimeMillis()- start);
        } catch (Exception e){
            return new ForwardResult(Status.IO_ERROR, null, e.getMessage(),System.currentTimeMillis()- start);
        }
    }

    private synchronized int nextStartIndex(int total){
       if(strategy== Strategy.ROUND_ROBIN){
           int index=RRindex;
           RRindex=(RRindex+1)%total;
           return index;
       }
       return 0;
    }

    static byte[] buildServFail(byte[] query){
        byte[] r= Arrays.copyOf(query, query.length);

        r[2]=(byte) (r[2] | 0x80);
        r[3]=(byte) ((r[3] & 0xF0) | 0x02);
        r[6]=0;
        r[7]=0;
        r[8]=0;
        r[9]=0;
        r[10]=0;
        r[11]=0;
        return r;
    }

    private static void sleep(long ms){
        if (ms <= 0) return;
        try{
            long jitter=ThreadLocalRandom.current().nextInt(30);
            Thread.sleep(ms+jitter);
        }
        catch(InterruptedException e) {Thread.currentThread().interrupt();}
    }
}
