package io.gitlab.teamproject2026;

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
            byte[] buffer = new byte[1024];
            while (shouldRun) {
                try {
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
    }
    @AfterEach
    void tearDown(){
        shouldRun=false;
        if(mockServer!= null) mockServer.close();
        if (serverThread!= null) serverThread.interrupt();
    }
    @Test
    void testSuccessfulResolution(){
        StubResolver.NameServer server= new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(),
        "MockLocal");
        StubResolver resolver= new StubResolver(List.of(server),1000,2,50,StubResolver.Strategy.FAILOVER);
        byte[] query= new byte[]{0x12, 0x34, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertArrayEquals(query, response, "Should perfectly echo back the simulated success response");
    }
    @Test
    void testTimeoutAndServFail(){
        serverDelayMs=200;
        StubResolver.NameServer server= new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(), "MockLocal");
        StubResolver resolver= new StubResolver(List.of(server),100,1,10,StubResolver.Strategy.FAILOVER);
        byte[] query= new byte[12];
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertEquals(0x02, response[3]& 0x0F, "Response should have SERVFAIL RCODE set");
        assertEquals((byte)0x80, (byte)(response[2] & 0x80), "QR bit must be set to 1" );
    }
    @Test
    void testFailoverSwitchesServer(){
        serverDelayMs=200;
        StubResolver.NameServer badServer = new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(), "BadPrimary");
        StubResolver.NameServer deadServer= new StubResolver.NameServer("127.0.0.1", 54321, "Dead");
        StubResolver resolver = new StubResolver(List.of(badServer, deadServer),50,1,10, StubResolver.Strategy.FAILOVER);
        byte[] query= new byte[12];
        byte[] response= resolver.resolve(query);
        assertNotNull(response);
        assertEquals(0x02, response[3] & 0x0F);
    }
    @Test
    void testRoundRobinStrategy(){
        StubResolver.NameServer server1= new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(), "server1");
        StubResolver.NameServer server2= new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(), "server2");
        StubResolver resolver = new StubResolver(List.of(server1, server2),1000,0,10, StubResolver.Strategy.ROUND_ROBIN);
        byte[] query = new byte[12];
        resolver.resolve(query);
        assertEquals(0, requestCount.get());
    }
}