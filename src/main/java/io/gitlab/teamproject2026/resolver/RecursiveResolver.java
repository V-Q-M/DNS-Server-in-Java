package io.gitlab.teamproject2026.resolver;

import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.RRType;
import io.gitlab.teamproject2026.model.OpCode;
import io.gitlab.teamproject2026.model.ResourceRecord;
import io.gitlab.teamproject2026.database.CacheHandler;

import java.sql.SQLException;
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

    private final CacheHandler cache;

    public RecursiveResolver(CacheHandler cache) {
        this.cache = cache;
    }

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

            // Insert everything this hop returned
            try {
                cache.insertAll(response.getAnswerSections());
                cache.insertAll(response.getAuthoritySections());
                cache.insertAll(response.getAdditionalSections());
            } catch (SQLException e) {
                //keep resolving
            }

            // which branch of the RCODE decision tree is this?
            ReferralAnalyzer.Classification verdict = ReferralAnalyzer.classify(response, qtype);

            switch (verdict) {
                // The response is the answer, a final SOA, or a DNS error: return it.
                case ANSWER, NODATA, NXDOMAIN, FORMERR -> {
                    return response;
                }
                // This server could not answer: try the next one.
                case SERVFAIL -> {
                    continue;
                }
                // Delegation: add the returned name servers, then keep walking down.
                case REFERRAL -> {
                    // TODO: extractDelegations(response) -> slist.addSListEntry(...)
                    continue;
                }
                // Alias: restart resolution at the canonical name.
                case CNAME_CHAIN -> {
                    // TODO: resolve(cnameTarget(response), qtype)
                    continue;
                }
            }
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

    // Builds a minimal response carrying just an rcode (skeleton terminal).
    private DNSPacket placeholderRespond(RCode rcode) {
        DNSPacket response = new DNSPacket();
        response.setRcode(rcode);
        response.setOpcode(OpCode.QUERY);
        response.setAnswerSections(new ResourceRecord[0]);
        response.setAuthoritySections(new ResourceRecord[0]);
        response.setAdditionalSections(new ResourceRecord[0]);
        return response;
    }

    // Temporary nested types, replaced by C's verdict and D's SLIST entry.

    // TODO(D): becomes the SLIST entry type (name, address, hierarchy level).
    private record Server(String name, String ip, int level) {
        boolean hasGlue() {
            return ip != null;
        }
    }
}
