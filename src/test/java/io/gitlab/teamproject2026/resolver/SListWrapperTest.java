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

// This disables the SListWrapper-Tests during normal Testrun, as the SList is heavily dependant on Cache functionality 
// manually run these tests via > mvn test -DlocalDB=true
@EnabledIfSystemProperty(named = "localDB", matches = "true")
class SListWrapperTest {
    private CacheHandler handler;

    private ResourceRecord testNS = new ResourceRecord(new String[] {"de"},RRClass.IN,30000,new RDataNS(new String[] {"example","de"}));
    private ResourceRecord testA = new ResourceRecord(new String[] {"example","de"},RRClass.IN,30000,new RDataA(new byte[]{neg(107), neg(105), neg(127), neg(1)}));
    private ResourceRecord testAAAA = new ResourceRecord(new String[] {"example","de"},RRClass.IN,30000,new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x0d),neg(0xc3),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x35)}));

    private SListEntry testEntryA = new SListEntry(
            new String[] {"example","de"},
            (RDataA)testA.getRdata(),
            new RDataAAAA(new byte[16])
        );

    private SListEntry testEntryAAAA = new SListEntry(
            new String[] {"example","de"},
            new RDataA(new byte[4]),
            (RDataAAAA)testAAAA.getRdata()
        );

    /** Very simple helper function that formats unsigned byte values so that they can be correctly stored in the java byte type */
    /** This is a helper function only intended to hardcode the SBelt, do not use */
    private byte neg(int i){
        if (i>= 128){
            return (byte) (i-256);
        } else {
            return (byte) i;
        }
    }

    private void assertSListEntryEquals(SListEntry expected, SListEntry actual){
        assertArrayEquals(expected.NSname(), actual.NSname());
        if (expected.IPv4() == null|| actual.IPv4()==null){
            assertEquals(expected.IPv4(), actual.IPv4());
        }else {
            assertArrayEquals(expected.IPv4().getAddress(), actual.IPv4().getAddress());
        }

        if (expected.IPv6() == null|| actual.IPv6()==null){
            assertEquals(expected.IPv6(),expected.IPv6());
        }else{
            assertArrayEquals(expected.IPv6().getAddress(), actual.IPv6().getAddress());
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
    void SBeltLoadedWhenCacheEmpty() throws Exception {

        LinkedList<SListEntry> SBelt = new LinkedList<SListEntry>();

        SBelt.add(new SListEntry(new String[] {"a","root-servers","net"},new RDataA(new byte[] {neg(198),neg(41),neg(0),neg(4)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x03),neg(0xba),neg(0x3e),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x02),neg(0x00),neg(0x30)})));
        SBelt.add(new SListEntry(new String[] {"b","root-servers","net"},new RDataA(new byte[] {neg(170),neg(247),neg(170),neg(2)}), new RDataAAAA(new byte[] {neg(0x28),neg(0x01),neg(0x01),neg(0xb8),neg(0x00),neg(0x10),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0b)})));
        SBelt.add(new SListEntry(new String[] {"c","root-servers","net"},new RDataA(new byte[] {neg(192),neg(33),neg(4),neg(12)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x02),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0c)})));
        SBelt.add(new SListEntry(new String[] {"d","root-servers","net"},new RDataA(new byte[] {neg(199),neg(7),neg(91),neg(13)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x2d),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0d)})));
        SBelt.add(new SListEntry(new String[] {"e","root-servers","net"},new RDataA(new byte[] {neg(192),neg(203),neg(230),neg(10)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0xa8),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0e)})));
        SBelt.add(new SListEntry(new String[] {"f","root-servers","net"},new RDataA(new byte[] {neg(192),neg(5),neg(5),neg(241)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x2f),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0f)})));
        SBelt.add(new SListEntry(new String[] {"g","root-servers","net"},new RDataA(new byte[] {neg(192),neg(112),neg(36),neg(4)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x12),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x0d),neg(0x0d)})));
        SBelt.add(new SListEntry(new String[] {"h","root-servers","net"},new RDataA(new byte[] {neg(198),neg(97),neg(190),neg(53)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x01),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x53)})));
        SBelt.add(new SListEntry(new String[] {"i","root-servers","net"},new RDataA(new byte[] {neg(192),neg(58),neg(128),neg(30)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x03),neg(0x0c),neg(0x27),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x02),neg(0x00),neg(0x30)})));
        SBelt.add(new SListEntry(new String[] {"j","root-servers","net"},new RDataA(new byte[] {neg(192),neg(36),neg(148),neg(17)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x07),neg(0xfe),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x53)})));
        SBelt.add(new SListEntry(new String[] {"k","root-servers","net"},new RDataA(new byte[] {neg(193),neg(0),neg(14),neg(129)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x07),neg(0xfd),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x01)})));
        SBelt.add(new SListEntry(new String[] {"l","root-servers","net"},new RDataA(new byte[] {neg(199),neg(7),neg(83),neg(42)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x05),neg(0x00),neg(0x00),neg(0x9f),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x42)})));
        SBelt.add(new SListEntry(new String[] {"m","root-servers","net"},new RDataA(new byte[] {neg(202),neg(12),neg(27),neg(33)}), new RDataAAAA(new byte[] {neg(0x20),neg(0x01),neg(0x0d),neg(0xc3),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x00),neg(0x35)})));

        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        SListEntry bestServer = SList.getBestServer();
        assertArrayEquals(bestServer.NSname(), SBelt.getFirst().NSname());
        assertArrayEquals(bestServer.IPv4().getAddress(), SBelt.getFirst().IPv4().getAddress());
        assertArrayEquals(bestServer.IPv6().getAddress(), SBelt.getFirst().IPv6().getAddress());
        assertTrue(SList.getCurrentMatchLevel()==0);
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
        
        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertEquals(SList.getMatchLevel(new String[]{}),0);
        assertEquals(SList.getMatchLevel(new String[]{"de"}),1);
        assertEquals(SList.getMatchLevel(new String[]{"uni-tuebingen","de"}),2);
        assertEquals(SList.getMatchLevel(new String[]{"example","de"}),1);
        assertEquals(SList.getMatchLevel(new String[]{"co","uk"}),0);
    }

    @Test 
    void getBestServerRemovesServer() throws Exception {
         handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );

        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        SList.getBestServer();
        assertSListEntryEquals(SList.getSList().getFirst(), testEntryAAAA);
        SList.getBestServer();
        assertEquals(SList.getSList(), new LinkedList<SListEntry>());

        assertEquals(SList.getBestServer().NSname().length,0);
        
    }

    @Test
    void getBestServerReturnsFirstServer() throws Exception{
         handler.insertAll(
            new ResourceRecord[] {
                testNS,testA,testAAAA
            }
        );
        
        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        assertSListEntryEquals(SList.getBestServer(), testEntryA);

        assertSListEntryEquals(SList.getBestServer(),testEntryAAAA);

        assertEquals(SList.getBestServer().NSname().length, 0);
    }
    

    @Test
    void addSListEntryClearsAndAddsWhenMatchLevelHigher()throws Exception{

        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        SList.addSListEntry(testNS, (RDataA)testA.getRdata(), new RDataAAAA(new byte[16]));

        assertSListEntryEquals(SList.getBestServer(), testEntryA);
    }

    @Test
    void addListEntryAddsWhenMatchLevelEqual() throws Exception {
        handler.insertAll(new ResourceRecord[]{testA,testNS});


        SListWrapper SList = new SListWrapper(new String[] {"uni-tuebingen","de"}, handler);

        SList.addSListEntry(testNS, new RDataA(new byte[4]), (RDataAAAA)testAAAA.getRdata());

        assertSListEntryEquals(SList.getSList().getFirst(),testEntryA);
        assertSListEntryEquals(SList.getSList().getLast(), testEntryAAAA);
    }

}