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
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.model.RRType;


public class SListWrapper {

    private final String[] SName;

    // Model SList entries as a Record containing an Array of Labels, the name of the NS, an RDataA and an RDataAAAA Object, the Address of that particular instance
    public record SListEntry(RDataNS NSname, RDataA IPv4, RDataAAAA IPv6){
        @Override 
        public String toString(){
            String IPv4String="";
            String IPv6String="";
            String NSnameString = String.join(".", NSname.getNsdname()) + ".";
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

    private static final SListEntry[] SBelt = new SListEntry[] {

        // fill in SBelt 
        // no, this didn't take long at all, but i refuse to use AI
        new SListEntry(new RDataNS(new String[] {"a","root-servers","net"}),new RDataA(new byte[] {(byte)(198),(byte)(41),(byte)(0),(byte)(4)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x03),(byte)(0xba),(byte)(0x3e),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x30)})),
        new SListEntry(new RDataNS(new String[] {"b","root-servers","net"}),new RDataA(new byte[] {(byte)(170),(byte)(247),(byte)(170),(byte)(2)}), new RDataAAAA(new byte[] {(byte)(0x28),(byte)(0x01),(byte)(0x01),(byte)(0xb8),(byte)(0x00),(byte)(0x10),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0b)})),
        new SListEntry(new RDataNS(new String[] {"c","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(33),(byte)(4),(byte)(12)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0c)})),
        new SListEntry(new RDataNS(new String[] {"d","root-servers","net"}),new RDataA(new byte[] {(byte)(199),(byte)(7),(byte)(91),(byte)(13)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x2d),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0d)})),
        new SListEntry(new RDataNS(new String[] {"e","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(203),(byte)(230),(byte)(10)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0xa8),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0e)})),
        new SListEntry(new RDataNS(new String[] {"f","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(5),(byte)(5),(byte)(241)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x2f),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0f)})),
        new SListEntry(new RDataNS(new String[] {"g","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(112),(byte)(36),(byte)(4)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x12),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x0d),(byte)(0x0d)})),
        new SListEntry(new RDataNS(new String[] {"h","root-servers","net"}),new RDataA(new byte[] {(byte)(198),(byte)(97),(byte)(190),(byte)(53)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x01),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x53)})),
        new SListEntry(new RDataNS(new String[] {"i","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(58),(byte)(128),(byte)(30)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x03),(byte)(0x0c),(byte)(0x27),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x02),(byte)(0x00),(byte)(0x30)})),
        new SListEntry(new RDataNS(new String[] {"j","root-servers","net"}),new RDataA(new byte[] {(byte)(192),(byte)(36),(byte)(148),(byte)(17)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x07),(byte)(0xfe),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x53)})),
        new SListEntry(new RDataNS(new String[] {"k","root-servers","net"}),new RDataA(new byte[] {(byte)(193),(byte)(0),(byte)(14),(byte)(129)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x07),(byte)(0xfd),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x01)})),
        new SListEntry(new RDataNS(new String[] {"l","root-servers","net"}),new RDataA(new byte[] {(byte)(199),(byte)(7),(byte)(83),(byte)(42)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x05),(byte)(0x00),(byte)(0x00),(byte)(0x9f),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x42)})),
        new SListEntry(new RDataNS(new String[] {"m","root-servers","net"}),new RDataA(new byte[] {(byte)(202),(byte)(12),(byte)(27),(byte)(33)}), new RDataAAAA(new byte[] {(byte)(0x20),(byte)(0x01),(byte)(0x0d),(byte)(0xc3),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x00),(byte)(0x35)})),
    };

    private int currentMatchLevel=0;
    

    public String[] getSName(){
        return SName;
    }


    public int getCurrentMatchLevel(){
        return currentMatchLevel;
    }

    public LinkedList<SListEntry> getSList(){
        return SList;
    }

    /** Constructor for the SListWrapper class
     *  Creates an SList for a given SName and fills it with the closest cached entries
     *  Entries take the form of a Name and either an IPv4 or IPv6 Address Rdata field. The other field will contain an RData Object containing a 0 address
     * 
     * @param SName the Sname which the SList is dedicated to
     * @param zone A Zonehandler to check whether a NameServer is contained in the Zone 
     * @param cache A cachehandler that is used to check for already cached records
     * @throws SQLException 
     */
    public SListWrapper(RDataNS SName, CacheHandler cache) throws SQLException{
        this.SName = SName.getNsdname();

        // fill SList from Cache 
        for(int i = 0; i <= this.SName.length; i++){
            String query = String.join(".", Arrays.copyOfRange(this.SName,i , this.SName.length));

            ResourceRecord[] nameServers;

            nameServers = cache.lookup(query,RRType.NS);

            for (ResourceRecord nameServer:nameServers) {
                ResourceRecord[] ipv4;
                ResourceRecord[] ipv6;

                
                ipv4 = cache.lookup(String.join(".", ((RDataNS)nameServer.getRdata()).getNsdname()),RRType.A);
                ipv6 = cache.lookup(String.join(".", ((RDataNS)nameServer.getRdata()).getNsdname()),RRType.AAAA);

                if (ipv4.length == 0 && ipv6.length == 0){
                    SList.add(new SListEntry((RDataNS)nameServer.getRdata(), new RDataA(new byte[4]), new RDataAAAA(new byte[16])));
                    break;
                } else {
                    // if no addresses are cached, skip this loop iteration, else add an SListEntry for every address and break
                    currentMatchLevel = this.SName.length - i;
                    for (ResourceRecord address :ipv4){
                        SList.add(new SListEntry(((RDataNS)nameServer.getRdata()), (RDataA)address.getRdata(), new RDataAAAA(new byte[16])));
                    }
                    for (ResourceRecord address :ipv6){
                        SList.add(new SListEntry(((RDataNS)nameServer.getRdata()), new RDataA(new byte[4]), (RDataAAAA)address.getRdata()));
                    }
                    break;
                }
            
            }   
            
        }
        if(SList.isEmpty()){
            for (SListEntry entry : SBelt){
                SList.add(entry);
            }
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
                SList.add(new SListEntry(((RDataNS)NS.getRdata()), ipv4,ipv6));
            } else if (matchLevel == currentMatchLevel){
                SList.add(new SListEntry(((RDataNS)NS.getRdata()), ipv4,ipv6));
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
     * @return An SListEntry containing the Name and the addresses of the current best guess for an authoratative NS for the SName and an SListEntry with a Labelarray of length 0 if SList is empty
     */
    public SListEntry getBestServer(){
        try {
            return SList.removeFirst();
        } catch (NoSuchElementException e) {
            return new SListEntry(new RDataNS(new String[]{}), new RDataA(), new RDataAAAA());
        }
    }


}