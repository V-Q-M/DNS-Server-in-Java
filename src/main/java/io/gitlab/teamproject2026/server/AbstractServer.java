package io.gitlab.teamproject2026.server;

import static io.gitlab.teamproject2026.AnsiColors.*;

import io.gitlab.teamproject2026.config.logging.LoggingCategoryType;
import io.gitlab.teamproject2026.config.logging.LoggingSeverity;
import io.gitlab.teamproject2026.database.CacheHandler;
import io.gitlab.teamproject2026.database.ZoneHandler;
import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.logging.DNSEventType;
import io.gitlab.teamproject2026.logging.Logger;
import io.gitlab.teamproject2026.logging.ResponseSource;
import io.gitlab.teamproject2026.model.DNSPacket;
import io.gitlab.teamproject2026.model.QuestionSection;
import io.gitlab.teamproject2026.model.RCode;
import io.gitlab.teamproject2026.model.ResourceRecord;
import java.net.InetAddress;
import java.util.LinkedList;

public abstract class AbstractServer implements Runnable {

    protected final CacheHandler cacheHandler;
    protected final ZoneHandler zoneHandler;
    protected final DNSResolutionPipeline resolutionPipeline;
    protected final DNSResponseBuilder responseBuilder;
    protected final Logger logger;
    protected final int portNumber;
    protected final InetAddress bindAddress;

    protected final int ednsUdpSizeLimit;

    protected final boolean recursionAllowed;

    public AbstractServer(ServerContext context) {
        this.cacheHandler = context.cacheHandler();
        this.zoneHandler = context.zoneHandler();
        this.resolutionPipeline = context.resolutionPipeline();
        this.responseBuilder = context.responseBuilder();
        this.bindAddress = context.bindAddress();
        this.portNumber = context.portNumber();
        this.ednsUdpSizeLimit = context.ednsUdpSizeLimit();
        this.logger = context.logger();
        this.recursionAllowed = context.serverConfig().options().recursionAllowed();
    }

    /** Handles an incoming DNS query and produces the corresponding response. */
    protected byte[] handleQuery(byte[] data) {
        // Map byteData to oop classes
        DNSPacket oopPacket = DNSPacket.fromBytes(data);

        // Uncomment this if you need to debug incoming packets
        // IO.println("[SERVER] Assembled Packet:\n" + oopPacket);

        // Respond: If requested RR is cached, return RR else return stub Response

        DNSPayload payload =
                new DNSPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(), new LinkedList<>());

        // needed for cache logging:
        String id = oopPacket.getIdString();

        // first question for Trace-logs

        logger.logTrace(
                "Received query for RR Type: " + oopPacket.getQuestionSection(0).getQtype(), oopPacket);

        boolean authoritative = false;
        if (cacheHandler != null && zoneHandler != null) {
            // Step 1: Check authoritative zones first (Zone -> Cache -> Forward)
            logger.logTrace("Try resolving from Zones.", oopPacket);
            payload = resolutionPipeline.zoneLookUp(oopPacket, id);

            // If recursion is disabled and something is outside our zones, REFUSE
            if (!recursionAllowed && !payload.notResolved().isEmpty()) {
                logger.log(
                        (RED + "[SERVER] Received a foreign query but recursion is currently not allowed!"),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true);

                logger.logTrace(
                        "Query not contained in Zones but no recursion allowed. Return " + RCode.REFUSED, oopPacket);

                // return response.toBytes();
                return responseBuilder.refused(payload, oopPacket).toBytes();
            }

            // If all questions matched an authoritative zone but no records exist -> NXDOMAIN
            // True NXDOMAIN only when ALL sections are empty:
            // Explicit NS queries are returned in the answer section.
            // Authority may still contain delegation/referral-style records.

            if (payload.rcode() == RCode.NXDOMAIN) {
                logger.logTrace(
                        "Query contained in Zones but no matching entries were found. Return " + RCode.NXDOMAIN,
                        oopPacket);
                return responseBuilder.nxDomain(payload, oopPacket, true).toBytes();
            }

            if (payload.notResolved().isEmpty() && !payload.answer().isEmpty()) {
                logger.logTrace("Query contained in Zones", oopPacket);
                String answerString = "";
                for (ResourceRecord answer : payload.answer()) {
                    answerString += "\n\t\t" + answer.getRdata().toString();
                }
                logger.logTrace("Answered: " + answerString, oopPacket);
                return responseBuilder.noError(payload, oopPacket, true).toBytes();
            }

            // Step 2: Cache lookup only for questions not resolved by zone
            if (payload.notResolved().isEmpty()) {
                authoritative = true;
            }

            if (!payload.notResolved().isEmpty()) {
                logger.logTrace("Could not resolve query from Zones, try Cache instead", oopPacket);
            }

            payload = resolutionPipeline.cacheLookUp(payload, oopPacket.getEdnsRecord(), id);

            if (payload.notResolved().isEmpty()) {
                logger.logTrace("Query resolved from Cache", oopPacket);
            } else {
                logger.logTrace("Query could not be resolved from Cache, try Resolver instead.", oopPacket);
            }

        } else {
            // if Cache can't be reached
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                payload.notResolved().add(request);

                logger.logTrace(
                        "Query could not be resolved from Cache or Zone due to missing database connection, try Resolver instead.",
                        oopPacket);

                logger.log(
                        (DNSEvent.event(
                                        DNSEventType.CACHE_MISS,
                                        id,
                                        request.nameToString(),
                                        request.getQtype(),
                                        0L,
                                        ResponseSource.CACHE,
                                        resolutionPipeline
                                                .resolverOptions
                                                .strategy()
                                                .toString(),
                                        "Local Cache",
                                        "127.0.0.1",
                                        1,
                                        "testing is active, do not check cache",
                                        RCode.UNASSIGNED)
                                .toHumanReadable()),
                        LoggingCategoryType.QUERIES,
                        LoggingSeverity.NOTICE);
            }
        }
        // Stub/RecursiveResolver should only be queried if there are queries not answered by the
        // cache and recursion is allowed
        if (recursionAllowed && !payload.notResolved().isEmpty()) {
            payload = resolutionPipeline.resolveUpstream(payload, oopPacket.getEdnsRecord(), data);
        }

        logger.logTrace("Query was successfully resolved", oopPacket);
        String answerString = "";
        for (ResourceRecord answer : payload.answer()) {
            answerString += "\n\t\t" + answer.getRdata().toString();
        }
        logger.logTrace("Answered: " + answerString, oopPacket);

        if (payload.rcode() == RCode.NXDOMAIN) {
            return responseBuilder.nxDomain(payload, oopPacket, authoritative).toBytes();
        }

        if (!payload.notResolved().isEmpty()) {
            return responseBuilder.servFail(payload, oopPacket).toBytes();
        }

        return responseBuilder.noError(payload, oopPacket, authoritative).toBytes();
    }
}
