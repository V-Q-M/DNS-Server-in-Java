package io.gitlab.teamproject2026;

import io.gitlab.teamproject2026.resolver.Resolver;
import io.gitlab.teamproject2026.resolver.StubResolverStrategy;
import io.gitlab.teamproject2026.resolver.StubResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StubResolverTest {
    private DatagramSocket mockServer;
    private Thread serverThread;
    private volatile boolean shouldRun = true;
    private volatile long serverDelayMs = 0;
    private final AtomicInteger requestCount= new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        shouldRun = true;
        serverDelayMs = 0;
        requestCount.set(0);
        mockServer = new DatagramSocket(0);
        serverThread = new Thread(() -> {
            
            while (shouldRun) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    mockServer.receive(packet);
                    if (serverDelayMs > 0) {
                        Thread.sleep(serverDelayMs);
                    }
                    byte[] responseData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, responseData, 0, packet.getLength());
                    DatagramPacket response = new DatagramPacket(responseData, responseData.length,
                            packet.getAddress(), packet.getPort());
                    mockServer.send(response);
                } catch (Exception e) {
                    break;
                }
            }
        });
        serverThread.start();
        Thread.sleep(20);
    }
    @AfterEach
    void tearDown(){
        shouldRun=false;
        if(mockServer!= null) mockServer.close();
        if (serverThread!= null) serverThread.interrupt();
    }
    @Test
    void testSuccessfulResolution(){
        Resolver server = new Resolver("127.0.0.1", mockServer.getLocalPort(),
                "MockLocal");
        StubResolver resolver= new StubResolver(List.of(server),1000,2,50, StubResolverStrategy.FAILOVER);
        byte[] query= new byte[]{0x12, 0x34, // id
        0x00, 0x01, // flags, qdcount=1
        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
        0x00,       // root domain name".""
        0x00, 0x01, //qType A
        0x00, 0x01};  // qClass IN
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertArrayEquals(query, response, "Should perfectly echo back the simulated success response");
    }
    @Test
    void testTimeoutAndServFail(){
        serverDelayMs=200;
        Resolver server= new Resolver("127.0.0.1", mockServer.getLocalPort(), "MockLocal");
        StubResolver resolver= new StubResolver(List.of(server),100,1,10, StubResolverStrategy.FAILOVER);
        byte[] query= new byte[12];
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertEquals(0x02, response[3]& 0x0F, "Response should have SERVFAIL RCODE set");
        assertEquals((byte)0x80, (byte)(response[2] & 0x80), "QR bit must be set to 1" );
    }
    @Test
    void testFailoverSwitchesServer(){
        serverDelayMs=200;
        Resolver badServer = new Resolver("127.0.0.1", mockServer.getLocalPort(), "BadPrimary");
        Resolver deadServer= new Resolver("127.0.0.1", 54321, "Dead");
        StubResolver resolver = new StubResolver(List.of(badServer, deadServer),50,1,10, StubResolverStrategy.FAILOVER);
        byte[] query= new byte[12];
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertEquals(0x02, response[3] & 0x0F);
    }
    @Test
    void testRoundRobinStrategy(){
        Resolver server1 = new Resolver("127.0.0.1", mockServer.getLocalPort(), "server1");
        Resolver server2 = new Resolver("127.0.0.1", mockServer.getLocalPort(), "server2");
        StubResolver resolver = new StubResolver(List.of(server1, server2),1000,0,10, StubResolverStrategy.ROUND_ROBIN);
        byte[] query = new byte[12];
        resolver.resolve(query);
        assertEquals(0, requestCount.get());
    }

    @Test
    void testShortResponse() throws Exception {
        DatagramSocket shortServer = new DatagramSocket(0);
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[1024];
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                shortServer.receive(packet);
                byte[] shortResponse = new byte[]{0x12, 0x34, 0x00, 0x00}; // only 4 bytes, invalid DNS
                shortServer.send(new DatagramPacket(shortResponse, shortResponse.length, packet.getAddress(), packet.getPort()));
            } catch (Exception ignored) {}
        });
        t.start();
        try {
            Resolver server = new Resolver("127.0.0.1", shortServer.getLocalPort(), "ShortResponse");
            StubResolver resolver = new StubResolver(List.of(server), 500, 0, 0, StubResolverStrategy.FAILOVER);
            byte[] query = new byte[]{0x12, 0x34, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] response = resolver.resolve(query);
            assertEquals(0x02, response[3] & 0x0F, "Response shorter than 12 bytes should result in SERVFAIL");
        } finally {
            shortServer.close();
            t.interrupt();
        }
    }

    @Test
    void testBadTransactionId() throws Exception {
        DatagramSocket badIdServer = new DatagramSocket(0);
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[1024];
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                badIdServer.receive(packet);
                byte[] responseData = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, responseData, 0, packet.getLength());
                responseData[0] = (byte) ~responseData[0]; // flip transaction ID byte 0
                badIdServer.send(new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort()));
            } catch (Exception ignored) {}
        });
        t.start();
        try {
            Resolver server = new Resolver("127.0.0.1", badIdServer.getLocalPort(), "BadId");
            StubResolver resolver = new StubResolver(List.of(server), 500, 0, 0, StubResolverStrategy.FAILOVER);
            byte[] query = new byte[]{0x12, 0x34, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] response = resolver.resolve(query);
            assertEquals(0x02, response[3] & 0x0F, "Transaction ID mismatch should result in SERVFAIL");
        } finally {
            badIdServer.close();
            t.interrupt();
        }
    }

}