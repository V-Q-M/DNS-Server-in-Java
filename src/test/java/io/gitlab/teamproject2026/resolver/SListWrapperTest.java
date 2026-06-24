package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.resolver.SListWrapper;
import io.gitlab.teamproject2026.resolver.SListWrapper.SListEntry;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.model.RRClass;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import io.gitlab.teamproject2026.model.rdata.RDataAAAA;
import io.gitlab.teamproject2026.model.rdata.RDataNS;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedList;

import java.sql.SQLException;


class SListWrapperTest {
    private CacheHandler handler;

    private ResourceRecord testNS = new ResourceRecord(new String[] {"de"},RRClass.IN,30000,new RDataNS(new String[] {"example","de"}));
    private ResourceRecord testA = new ResourceRecord(new String[] {"example","de"},RRClass.IN,30000,new RDataA(new byte[]{(byte)(107), (byte)(105), (byte)(127), (byte)(1)}));
    private ResourceRecord testAAAA = new ResourceRecord(new String[] {"example","de"},RRClass.IN,30000,new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x0d),(byte)(0xc3),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x35)}));

    private SListEntry testEntryA = new SListEntry(
            new RDataNS(new String[] {"example","de"}),
            (RDataA)testA.getRdata(),
            new RDataAAAA(new byte[16])
        );

    private SListEntry testEntryAAAA = new SListEntry(
            new RDataNS(new String[] {"example","de"}),
            new RDataA(new byte[4]),
            (RDataAAAA)testAAAA.getRdata()
        );


    private void assertSListEntryEquals(SListEntry expected, SListEntry actual){
        assertArrayEquals(expected.nsName().getNsdname(), actual.nsName().getNsdname());

        if (expected.ipv4() == null|| actual.ipv4()==null){
            assertEquals(expected.ipv4(), actual.ipv4());
        }else {
            assertArrayEquals(expected.ipv4().getAddress(), actual.ipv4().getAddress());
        }

        if (expected.ipv6() == null|| actual.ipv6()==null){
            assertEquals(expected.ipv6(),expected.ipv6());
        }else{
            assertArrayEquals(expected.ipv6().getAddress(), actual.ipv6().getAddress());
        }
    }

    @BeforeEach
    void setup() throws Exception {
        handler = new CacheHandler(new DatabaseConnectionProvider());
        handler.clearAll();
    }

    @AfterEach
    void reset() throws Exception {
        handler.clearAll();
    }

    void cacheSetup() throws Exception{
        handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );
    }


    @Test 
    void sBeltLoadedWhenCacheEmpty() throws Exception {

        LinkedList<SListEntry> sBelt = new LinkedList<SListEntry>();

        sBelt.add(new SListEntry(new RDataNS(new String[] {"a","root-servers","net"}),new RDataA(new byte[] {(byte)(198),(byte)(41),(byte)(0),(byte)(4)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x03),(byte)(0xba),(byte)(0x3e),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x30)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"b","root-servers","net"}),new RDataA(new byte[] {(byte)(170),(byte)(247),(byte)(170),(byte)(2)}), new RDataAAAA(new byte[] {(byte)(0x28),(byte)(0x01),(byte)(0x01),(byte)(0xb8),(byte)(0x00),(byte)(0x10),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0b)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"c","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(33),(byte)(4),(byte)(12)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0c)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"d","root-servers","net"}),new RDataA(new byte[] {(byte)(199),(byte)(7),(byte)(91),(byte)(13)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x2d),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0d)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"e","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(203),(byte)(230),(byte)(10)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0xa8),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0e)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"f","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(5),(byte)(5),(byte)(241)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x2f),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0f)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"g","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(112),(byte)(36),(byte)(4)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x12),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0d),(byte)(0x0d)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"h","root-servers","net"}),new RDataA(new byte[] {(byte)(198),(byte)(97),(byte)(190),(byte)(53)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x01),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x53)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"i","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(58),(byte)(128),(byte)(30)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x03),(byte)(0x0c),(byte)(0x27),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x30)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"j","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(36),(byte)(148),(byte)(17)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x07),(byte)(0xfe),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x53)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"k","root-servers","net"}),new RDataA(new byte[] {(byte)(193),(byte)(0),(byte)(14),(byte)(129)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x07),(byte)(0xfd),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x01)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"l","root-servers","net"}),new RDataA(new byte[] {(byte)(199),(byte)(7),(byte)(83),(byte)(42)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x9f),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x42)})));
        sBelt.add(new SListEntry(new RDataNS(new String[] {"m","root-servers","net"}),new RDataA(new byte[] {(byte)(202),(byte)(12),(byte)(27),(byte)(33)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x0d),(byte)(0xc3),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x35)})));

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        SListEntry bestServer = sList.getBestServer();
        assertArrayEquals(bestServer.nsName().getNsdname(), sBelt.getFirst().nsName().getNsdname());
        assertArrayEquals(bestServer.ipv4().getAddress(), sBelt.getFirst().ipv4().getAddress());
        assertArrayEquals(bestServer.ipv6().getAddress(), sBelt.getFirst().ipv6().getAddress());
        assertTrue(sList.getCurrentMatchLevel()==0);
    }

    @Test
    void loadingEntriesFromCache() throws Exception{
         handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );

        LinkedList<SListEntry> result = new LinkedList<>();

        result.add(testEntryA);
        result.add(testEntryAAAA);

        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);
        assertSListEntryEquals(SList.getSList().getFirst(), result.getFirst());
        assertSListEntryEquals(SList.getSList().getLast(), result.getLast());
        assertEquals(SList.getCurrentMatchLevel(),1);

            
    }

    @Test
    void calculatingMatchLevel() throws Exception {
        
        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(sList.getMatchLevel(new String[]{}),0);
        assertEquals(sList.getMatchLevel(new String[]{"de"}),1);
        assertEquals(sList.getMatchLevel(new String[]{"uni-tuebingen","de"}),2);
        assertEquals(sList.getMatchLevel(new String[]{"example","de"}),1);
        assertEquals(sList.getMatchLevel(new String[]{"co","uk"}),0);
    }

    @Test 
    void getBestServerRemovesServer() throws Exception {
         handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        sList.getBestServer();
        assertSListEntryEquals(sList.getSList().getFirst(), testEntryAAAA);
        sList.getBestServer();
        assertEquals(sList.getSList(), new LinkedList<SListEntry>());

        assertEquals(sList.getBestServer().nsName().getNsdname().length,0);
        
    }

    @Test
    void getBestServerReturnsFirstServer() throws Exception{
         handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );
        
        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertSListEntryEquals(sList.getBestServer(), testEntryA);

        assertSListEntryEquals(sList.getBestServer(),testEntryAAAA);

        assertEquals(sList.getBestServer().nsName().getNsdname().length, 0);
    }
    

    @Test
    void addSListEntryClearsAndAddsWhenMatchLevelHigher()throws Exception{

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(sList.getCurrentMatchLevel(), 0);

        sList.addSListEntry(testNS, (RDataA)testA.getRdata(), new RDataAAAA(new byte[16]));

        assertSListEntryEquals(sList.getBestServer(), testEntryA);
        assertEquals(sList.getCurrentMatchLevel(), 1);
    }

    @Test
    void addListEntryAddsWhenMatchLevelEqual() throws Exception {
        handler.insertAll(new ResourceRecord[]{testA,testNS});


        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        sList.addSListEntry(testNS, new RDataA(new byte[4]), (RDataAAAA)testAAAA.getRdata());

        assertSListEntryEquals(sList.getSList().getFirst(),testEntryA);
        assertSListEntryEquals(sList.getSList().getLast(), testEntryAAAA);
    }

    @Test
    void readNSRecordsWithoutGlueRecords() throws Exception{
        handler.insertAll(new ResourceRecord[]{testNS});

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertSListEntryEquals(sList.getBestServer(), new SListEntry((RDataNS)testNS.getRdata(),new RDataA(new byte[4]),new RDataAAAA(new byte[16])));

    }

    @Test 
    void readMultipleLevelsFromCache() throws Exception{
        cacheSetup();

        handler.insertAll(new ResourceRecord[]{
            new ResourceRecord(new String[]{"uni-tuebingen","de"},RRClass.IN,30000,new RDataNS(new String[]{"test","uni-tuebingen","de"})),
            new ResourceRecord(new String[]{"test","uni-tuebingen","de"},RRClass.IN,30000,new RDataA(new byte[]{1,2,3,4}))
        });

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(sList.getCurrentMatchLevel(), 2);
        assertSListEntryEquals(sList.getBestServer(), new SListEntry(new RDataNS(new String[]{"test","uni-tuebingen","de"}), new RDataA(new byte[]{1,2,3,4}), new RDataAAAA(new byte[16])));
    }

    @Test 
    void readMultipleInstancesFromTheSameLevel() throws Exception {

        handler.insertAll(new ResourceRecord[]{
            new ResourceRecord(new String[]{"de"},RRClass.IN,30000,new RDataNS(new String[]{"example2","de"})),
            new ResourceRecord(new String[]{"example2","de"},RRClass.IN,30000,new RDataA(new byte[]{1,2,3,4})),
            testA,testAAAA,testNS
        });

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(sList.getSList().size(), 3);
    }

    @Test
    void addMultipleLayers() throws Exception {

        SListWrapper sList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(sList.getCurrentMatchLevel(), 0);

        sList.addSListEntry(testNS, (RDataA)testA.getRdata(), new RDataAAAA(new byte[16]));

        assertEquals(sList.getCurrentMatchLevel(), 1);

        sList.addSListEntry(
            new ResourceRecord(new String[]{"uni-tuebingen","de"},RRClass.IN,30000,new RDataNS(new String[]{"example","uni-tuebingen","de"})),
            new RDataA(new byte[]{1,2,3,4}),
            new RDataAAAA(new byte[16])
        );

        assertEquals(sList.getCurrentMatchLevel(), 2);

    }

}