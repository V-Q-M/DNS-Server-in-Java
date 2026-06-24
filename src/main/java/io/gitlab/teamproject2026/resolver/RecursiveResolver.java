package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;

import java.util.ArrayList;
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
    @Override
        public byte[] resolve(byte[] query) {
            DNSPacket queryPacket;
            try {
                queryPacket = DNSPacket.fromBytes(query);
            } catch (Exception e) {
                return buildServFail(query);
            }
            return resolve(queryPacket.getQuestionSections()[0]).toBytes();
        }


    // Public entry point: resolve one question.
    public DNSPacket resolve(QuestionSection question) {
        return resolve(question.getNameString(), question.getQtype());
    }

    // Re-entrant core. Resolves sname/qtype from a fresh SLIST.
    // Terminates because the SLIST strictly shrinks: 
    // - each pass either returns or removes a server.
    // Step 2 keeps this property:
    // - referrals prune higher-level
    // servers (so delegations only descend), and CNAME loops get a visited-set.
    private DNSPacket resolve(String sname, RRType qtype) {

        // Seed the SLIST from the closest known ancestor zone, else root hints.
        // TODO(B/D)
        List<Server> slist = new ArrayList<>(placeholderFindClosestNs(sname));

        while (true) {

            // TODO(D): find the best server.
            Server server = placeholderPickBestServer(slist);

            // SLIST empty.
            if (server == null) {
                return placeholderRespond(RCode.SERVFAIL);
            }
            slist.remove(server); 

            // TODO(B): send iterative query (RD cleared), parse, id-check.
            DNSPacket response = placeholderSendQuery(sname, qtype, server);

            // No response: try the next server.
            if (response == null) {
                continue;
            }

            // TODO(C): which branch of the RCODE decision tree is this?
            Verdict verdict = placeholderClassify(response, sname, qtype);

            // TODO(Step 2): act on verdict:
            //   ANSWER or NODATA  -> return to client
            //   NXDOMAIN or OTHER -> return error to client
            //   REFERRAL          -> update SLIST (drop higher levels) and loop
            //   CNAME             -> restart: resolve(canonicalName, qtype)
            //   SERVFAIL          -> try next server
            // Until then, return the raw response so the skeleton terminates.
            return response;
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
    private DNSPacket placeholderSendQuery(String sname, RRType qtype, Server server) {
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
