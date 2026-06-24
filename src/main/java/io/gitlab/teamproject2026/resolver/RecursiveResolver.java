package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.*;
import io.gitlab.teamproject2026.database.DatabaseConnectionProvider;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.rdata.RDataNS;
import io.gitlab.teamproject2026.resolver.SListWrapper.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/*
 * Iterative resolver loop (RFC 1034 5.3.3).
 *
 * Flow: seed SLIST -> pick server -> send query -> next server on no response
 *       -> SERVFAIL when SLIST is empty.
 *
 * Notes:
 * - resolve(name, ...) is recursive on purpose. CNAME restart and glue-less
 *   lookups (Step 2/3) just call it again with a different name.
 * - placeholder* methods are stand-ins until B (transport/cache), C (classify)
 *   and D (SLIST) land. Verdict and Server get replaced by their real types.
 */
public class RecursiveResolver extends AbstractResolver{
    //TODO: constructur including retrydelay,...
    @Override
        public byte[] resolve(byte[] query) throws Exception{
            DNSPacket queryPacket;
            try {
                queryPacket = DNSPacket.fromBytes(query);
            } catch (Exception e) {
                return buildServFail(query);
            }
            //TODO check local zone server &cache
            QuestionSection question= queryPacket.getQuestionSections()[0];
        // resolve one question.
            return resolve(question.getName(), question.getQtype(), query);
        }



    //important to keep because case of missing glue records needs seperate loop??


    // Re-entrant core. Resolves sname/qtype from a fresh SLIST.
    // Terminates because the SLIST strictly shrinks: 
    // - each pass either returns or removes a server.
    // Step 2 keeps this property:
    // - referrals prune higher-level
    // servers (so delegations only descend), and CNAME loops get a visited-set.
    private byte[] resolve(String[] sname, RRType qtype, byte[] query) throws Exception {

        // fill slist
        CacheHandler handler = new CacheHandler(new DatabaseConnectionProvider());
        handler.clearAll();
        SListWrapper slistWrap = new SListWrapper(sname, handler);
        LinkedList<SListEntry> slist = slistWrap.getSList();

        while (true) {

            //  find the best server
            SListEntry bestNsserver = slistWrap.getBestServer();
            // SLIST empty.
            if (bestNsserver == null) {
                return buildServFail(query);
            }

            //build NameServer for queryUDP/queryTCP
            String ipv4Str = (bestNsserver.ipv4() != null) ? bestNsserver.ipv4().toString() : "0.0.0.0";
            String ipv6Str = (bestNsserver.ipv6() != null) ? bestNsserver.ipv6().toString() : "0000:0000:0000:0000:0000:0000:0000:0000";
            String nsName = (bestNsserver.nsName() != null) ? bestNsserver.nsName().toString() : "unknown";

            NameServer queriedServerIpv4 = new NameServer(ipv4Str, 53, nsName);
            NameServer queriedServerIpv6 = new NameServer(ipv6Str, 53, nsName);

            // TODO(B): send iterative query (RD cleared), parse, id-check.
            QueryResult result = null;
            NameServer currentServer = null;
            //query UDP: try ipv4, if not available use ipv6
            if (!queriedServerIpv4.host().equals("0.0.0.0")) {
                currentServer = queriedServerIpv4;
                result = queryUDP(query, queriedServerIpv4, timeoutMs);
            } else if (!queriedServerIpv6.host().equals("0000:0000:0000:0000:0000:0000:0000:0000")){
                currentServer = queriedServerIpv6;
                result = queryUDP(query, queriedServerIpv6, timeoutMs);
            }

            //handle failures
            if (result==null||result.status()==Status.TIMEOUT||result.status()==Status.IO_ERROR) {
                slist.removeFirst();
                sleep(retryDelay);
                continue;
            }

            //fallback to TCP in case of truncation
            if (isTruncated(result.response())) {
                QueryResult tcpResult = queryTCP(query, currentServer, timeoutMs);
                if (tcpResult == null || tcpResult.status() != Status.SUCCESS) {
                    slist.removeFirst();
                    sleep(retryDelay);
                    continue;
                }
                result = tcpResult;
            }

            DNSPacket response;
            try {
                response = DNSPacket.fromBytes(result.response());
            } catch (Exception e) {
                slist.removeFirst();
                continue;
            }


            //DNSPacket response = placeholderSendQuery(sname, qtype, bestNsserver);


            // TODO(C): which branch of the RCODE decision tree is this?
            Verdict verdict = placeholderClassify(response, sname.toString(), qtype);

            // TODO(Step 2): act on verdict:
            //   ANSWER or NODATA  -> return to client
            //   NXDOMAIN or OTHER -> return error to client
            //   REFERRAL          -> update SLIST (drop higher levels) and loop
            //   CNAME             -> restart: resolve(canonicalName, qtype)
            //   SERVFAIL          -> try next server
            // Until then, return the raw response so the skeleton terminates.
            return response.toBytes();
        }
    }

    // Placeholders for B, C and D, replaced at integration.

    // TODO(B): CacheHandler.lookupClosestNs plus root hints to entry-point servers.
    private List<Server> placeholderFindClosestNs(String name) {
        return List.of();
    }

    // TODO(D): SLIST find the best server (null if empty).
    private Server placeholderPickBestServer(List<Server> slist) {
        return slist.isEmpty() ? null : slist.getFirst();
    }

    // TODO(B): NameServerClient, build iterative query, send, parse and id-check.
    private DNSPacket placeholderSendQuery(String[] sname, RRType qtype, SListEntry server) {
        return null;
    }

    // TODO(C): ReferralAnalyzer.classify, which RCODE branch is this.
    private Verdict placeholderClassify(DNSPacket response, String sname, RRType qtype) {
        return Verdict.OTHER;
    }

    // Builds a minimal response carrying just an rcode (skeleton terminal).
    private DNSPacket placeholderRespond(RCode rcode) {
        DNSPacket response = new DNSPacket();
        response.setRcode(rcode);
        return response;
    }

    // Temporary nested types, replaced by C's verdict and D's SLIST entry.

    // TODO(C): becomes ReferralAnalyzer's verdict.
    private enum Verdict { ANSWER, CNAME, REFERRAL, NODATA, NXDOMAIN, SERVFAIL, OTHER }

    // TODO(D): becomes the SLIST entry type (name, address, hierarchy level).
    private record Server(String name, String ip, int level) {
        boolean hasGlue() {
            return ip != null;
        }
    }
}
