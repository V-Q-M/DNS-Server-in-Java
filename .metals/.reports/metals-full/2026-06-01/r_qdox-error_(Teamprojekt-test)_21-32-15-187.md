error id: file://<WORKSPACE>/src/test/java/io/gitlab/teamproject2026/StubResolverTest.java
file://<WORKSPACE>/src/test/java/io/gitlab/teamproject2026/StubResolverTest.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[154,2]

error in qdox parser
file content:
```java
offset: 7283
uri: file://<WORKSPACE>/src/test/java/io/gitlab/teamproject2026/StubResolverTest.java
text:
```scala
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
        StubResolver.NameServer server= new StubResolver.NameServer("127.0.0.1", mockServer.getLocalPort(),
                "MockLocal");
        StubResolver resolver= new StubResolver(List.of(server),1000,2,50,StubResolver.Strategy.FAILOVER);
        byte[] query= new byte[]{0x12, 0x34, // ID
        0x00, 0x01, // Flags (QDCOUNT = 1)
        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Counts
        0x00,       // Root Domain name [.]
        0x00, 0x01, // QTYPE (A)
        0x00, 0x01  // QCLASS (IN)};
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
            StubResolver.NameServer server = new StubResolver.NameServer("127.0.0.1", shortServer.getLocalPort(), "ShortResponse");
            StubResolver resolver = new StubResolver(List.of(server), 500, 0, 0, StubResolver.Strategy.FAILOVER);
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
            StubResolver.NameServer server = new StubResolver.NameServer("127.0.0.1", badIdServer.getLocalPort(), "BadId");
            StubResolver resolver = new StubResolver(List.of(server), 500, 0, 0, StubResolver.Strategy.FAILOVER);
            byte[] query = new byte[]{0x12, 0x34, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] response = resolver.resolve(query);
            assertEquals(0x02, response[3] & 0x0F, "Transaction ID mismatch should result in SERVFAIL");
        } finally {
            badIdServer.close();
            t.interrupt();
        }
    }

}@@
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/test/java/io/gitlab/teamproject2026/StubResolverTest.java