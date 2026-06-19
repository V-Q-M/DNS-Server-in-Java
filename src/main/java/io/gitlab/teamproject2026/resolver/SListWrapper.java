package io.gitlab.teamproject2026.resolver;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.model.rdata.RDataA;
import io.gitlab.teamproject2026.model.rdata.RDataAAAA;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.model.RRType;


public class SListWrapper {

    private final String[] SName;

    // Model SList entries as a Record containing an Array of Labels, the name of the NS, an RDataA and an RDataAAAA Object, the Address of that particular instance
    public record SListEntry(String[] NSname, RDataA IPv4, RDataAAAA IPv6){
        @Override 
        public String toString(){
            String IPv4String="";
            String IPv6String="";
            String NSnameString = String.join(".", NSname) + ".";
            if (IPv4 != null){
                IPv4String = IPv4.toString();
            }
            if(IPv6 != null){
                IPv6String = IPv6.toString();
            }

            return "NSname: " + NSnameString + "\nIPv4: " + IPv4String + "\nIPv6: " + IPv6String;
        }
    }

    // Model SList as a LinkedList of SListEntries
    private LinkedList<SListEntry> SList = new LinkedList<SListEntry>(); 

    private ArrayList<SListEntry> SBelt = new ArrayList<SListEntry>();

    private int currentMatchLevel=0;
    

    public String[] getSName(){
        return SName;
    }

    /** Very simple helper function that formats unsigned byte values so that they can be correctly stored in the java byte type */
    /** This is a helper function only intended to hardcode the SBelt, do not use */
    private byte neg(int i){
        if (i>= 128){
            return (byte) (i-256);
        } else {
            return (byte) i;
        }
    }

    public int getCurrentMatchLevel(){
        return currentMatchLevel;
    }

    public LinkedList<SListEntry> getSList(){
        return SList;
    }

    /** Constructor for the SListWrapper class
     *  Creates an SList for a given SName and fills it with the closest cached entries
     *  Entries take the form of a Name and either an IPv4 or IPv6 Address Rdata field. The other field will contain an empty RDataA or RDataAAAA object
     * 
     * @param SName the Sname which the SList is dedicated to
     * @param zone A Zonehandler to check whether a NameServer is contained in the Zone 
     * @param cache A cachehandler that is used to check for already cached records
     * @throws SQLException 
     */
    public SListWrapper(String[] SName, CacheHandler cache) throws SQLException{
        this.SName = SName;


        // fill in SBelt 
        // no, this didn't take long at all, but i refuse to use AI
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

    

        // fill SList from Cache 
        for(int i = 0; i <= SName.length; i++){
            String query = String.join(".", Arrays.copyOfRange(SName,i , SName.length));

            ResourceRecord[] nameServers;

            try{
                nameServers = cache.lookup(query,RRType.NS);
            } catch (SQLException e) {
                throw e;
            }
            for (ResourceRecord nameServer:nameServers) {
                ResourceRecord[] ipv4;
                ResourceRecord[] ipv6;

                try {
                    ipv4 = cache.lookup(String.join(".", ((RDataNS)nameServer.getRdata()).getNsdname()),RRType.A);
                    ipv6 = cache.lookup(String.join(".", ((RDataNS)nameServer.getRdata()).getNsdname()),RRType.AAAA);
                } catch (SQLException e) {
                    throw e;
                }
                // if no addresses are cached, skip this loop iteration, else add an SListEntry for every address and break
                currentMatchLevel = SName.length - i;
                for (ResourceRecord address :ipv4){
                    SList.add(new SListEntry(((RDataNS)nameServer.getRdata()).getNsdname(), (RDataA)address.getRdata(), new RDataAAAA()));
                }
                for (ResourceRecord address :ipv6){
                    SList.add(new SListEntry(((RDataNS)nameServer.getRdata()).getNsdname(), new RDataA(), (RDataAAAA)address.getRdata()));
                }
                break;
            
            }   
            
        }
        if(SList.isEmpty()){
            SList.addAll(SBelt);
        }
        
    }

    /** Checks whether an Entry is relevant by comparing its MatchLevel(the number of labels that it shares with the SName) with the current MatchLevel
     * If the Level is lower, ignore
     * If the Level is Higher or equal but not equal the length of the the Url, ignore as it is not relevant to resolve SName
     * else: If the Level is equal current one: add the entry
     *       If the Level is higher than the current one: clear the SList and add the entry 
     * 
     * @param NS The NS ResourceRecord for the entry
     * @param ipv4 The IPv4 Address of the NS
     * @param ipv6 The IPv6 Address of the NS
     * @return
     */
    public void addSListEntry(ResourceRecord NS, RDataA ipv4, RDataAAAA ipv6){
        int matchLevel = getMatchLevel(NS.getName());
        if (matchLevel == NS.getName().length){
            if (matchLevel > currentMatchLevel){
                SList.clear();
                SList.add(new SListEntry(((RDataNS)NS.getRdata()).getNsdname(), ipv4,ipv6));
            } else if (matchLevel == currentMatchLevel){
                SList.add(new SListEntry(((RDataNS)NS.getRdata()).getNsdname(), ipv4,ipv6));
            }
        }
        
    }


    /** Calculates how many Labels of a given URL match the labels of the SName
     * 
     * @param url the url that is compared against
     * @return the match level of the given url
     */
    public int getMatchLevel(String[] url){
        // iterates through both SName and the url from the top lable to the lowest. 
        for(int i = 1; i <= SName.length; i++){

            if (url.length - i >= 0 && url[url.length-i].equals(SName[SName.length-i])){
                continue;
            } else {
                return i - 1;
            }
        }

        return SName.length;
    }

    /** Returns the current best NameServer that can be found and removes it from the SList
     * 
     * @return An SListEntry containing the Name and the addresses of the current best guess for an authoratative NS for the SName and an empty SListEntry if SList is empty
     */
    public SListEntry getBestServer(){
        try {
            return SList.removeFirst();
        } catch (NoSuchElementException e) {
            return new SListEntry(new String[]{}, new RDataA(), new RDataAAAA());
        }
    }


}