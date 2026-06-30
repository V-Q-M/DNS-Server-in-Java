package io.gitlab.teamproject2026.server;

import io.gitlab.teamproject2026.DNS_SERVER;
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
import java.util.HexFormat;
import java.util.LinkedList;

public abstract class AbstractServer implements Runnable {

    protected static final String RESET  = "\u001B[0m";
    protected static final String RED    = "\u001B[31m";
    protected static final String YELLOW = "\u001B[33m";
    protected static final String GREEN   = "\u001B[32m";
    protected static final String BLUE   = "\u001B[34m";
    protected static final String PINK = "\u001B[95m";

    protected final CacheHandler cacheHandler;
    protected final ZoneHandler zoneHandler;
    protected final DNSResolutionPipeline resolutionPipeline;
    protected final DNSResponseBuilder responseBuilder;
    protected final Logger logger;
    protected final int portNumber;
    protected final InetAddress bindAddress;

    public AbstractServer(CacheHandler cacheHandler, ZoneHandler zoneHandler,DNSResolutionPipeline resolutionPipeline, DNSResponseBuilder responseBuilder, InetAddress bindAddress, int portNumber, Logger logger){
        this.cacheHandler = cacheHandler;
        this.zoneHandler = zoneHandler;
        this.resolutionPipeline = resolutionPipeline;
        this.responseBuilder = responseBuilder;
        this.bindAddress = bindAddress;
        this.portNumber = portNumber;
        this.logger = logger;
    }

    /**
     * Handles an incoming DNS query and produces the corresponding response.
     */
    protected byte[] handleQuery(byte[] data){
        // Map byteData to oop classes
        DNSPacket oopPacket = DNSPacket.fromBytes(data);

        // Uncomment this if you need to debug outgoing packets
        //IO.println("[SERVER] Assembled Packet:\n" + oopPacket);

        // Respond: If requested RR is cached, return RR else return stub Response

        DNSPayload payload = new DNSPayload(new LinkedList<>(), new LinkedList<>(), new LinkedList<>(),
                new LinkedList<>());

        //needed for cache logging:
        byte[] idBytes = oopPacket.getId();
        String id = "0x" + HexFormat.of().formatHex(idBytes);

        //first question for Trace-logs

        logger.logTrace(PINK + "[TRACE] Received query for: " + oopPacket.getQuestionSection(0).getNameString() + " for RR Type: " + oopPacket.getQuestionSection(0).getQtype()+ RESET);

        boolean authoritative = false;
        if (cacheHandler != null && zoneHandler != null) {
            // Step 1: Check authoritative zones first (Zone -> Cache -> Forward)
            logger.logTrace(PINK + "[TRACE] Try resolving query: " + oopPacket.getQuestionSection(0).getNameString() +" from Zones."+ RESET);
            payload = resolutionPipeline.zoneLookUp(oopPacket, id);

            // If recursion is disabled and something is outside our zones, REFUSE
            if (!DNS_SERVER.recursionAllowed && !payload.notResolved().isEmpty()) {
                logger.log(
                        (RED + "[SERVER] Received a foreign query but recursion is currently not allowed!" ),
                        LoggingCategoryType.QUERY_ERRORS,
                        LoggingSeverity.ERROR,
                        true
                );

                logger.logTrace(PINK + "[TRACE] Query: " + oopPacket.getQuestionSection(0).getNameString() +" not contained in Zones but no recursion allowed. Return " + RCode.REFUSED+ RESET);

                //return response.toBytes();
                return responseBuilder.refused(payload, oopPacket).toBytes();
            }


            // If all questions matched an authoritative zone but no records exist -> NXDOMAIN
            // True NXDOMAIN only when ALL sections are empty:
            // authority may contain NS records from an authoritative zone answer

            if (payload.notResolved().isEmpty() && payload.answer().isEmpty() && payload.authority().isEmpty()) {
                DNSPacket response = new DNSPacket();
                response.setId(oopPacket.getId());
                response.setOpcode(oopPacket.getOpcode());
                response.setAa(true);
                response.setRd(oopPacket.isRd());
                response.setRa(DNS_SERVER.recursionAllowed);
                response.setRcode(RCode.NXDOMAIN);
                response.setQuestionSections(oopPacket.getQuestionSections());
                response.setAnswerSections(new ResourceRecord[0]);
                response.setAuthoritySections(new ResourceRecord[0]);
                response.setAdditionalSections(new ResourceRecord[0]);

                logger.logTrace(PINK + "[TRACE] Query: " + oopPacket.getQuestionSection(0).getNameString() +" contained in Zones but no matching entries were found. Return " + RCode.NXDOMAIN+ RESET);
            }

            if(payload.notResolved().isEmpty() && !payload.answer().isEmpty()
                    && payload.authority().isEmpty() ){
                DNSPacket response = new DNSPacket();
                response.setId(oopPacket.getId());
                response.setOpcode(oopPacket.getOpcode());
                response.setAa(true);
                response.setRd(oopPacket.isRd());
                response.setRa(DNS_SERVER.recursionAllowed);
                response.setRcode(RCode.NXDOMAIN);
                response.setQuestionSections(oopPacket.getQuestionSections());
                response.setAnswerSections(payload.answer().toArray(new ResourceRecord[0]));
                response.setAuthoritySections(new ResourceRecord[0]);
                response.setAdditionalSections(new ResourceRecord[0]);

                logger.logTrace(PINK + "[TRACE] Query: " + oopPacket.getQuestionSection(0).getNameString() +" contained in Zones"+ RESET
                );
                for (ResourceRecord answer: payload.answer()){
                    logger.logTrace(PINK + "[TRACE] Answer: " + String.join(".",answer.getName()) + "." + "of Type: " + answer.getType() + " with RData: " + answer.getRdata().toString() + RESET);
                }
                for (ResourceRecord authority: payload.authority()){
                    logger.logTrace(PINK + "[TRACE] Authority: " + String.join(".",authority.getName()) + "." + "of Type: " + authority.getType() + " with RData: " + authority.getRdata().toString() + RESET);
                }
                for (ResourceRecord additional: payload.additional()){
                    logger.logTrace(PINK + "[TRACE] Additional: " + String.join(".",additional.getName()) + "." + "of Type: " + additional.getType() + " with RData: " + additional.getRdata().toString() + RESET);
                }

                return response.toBytes();
            }

            // Step 2: Cache lookup only for questions not resolved by zone
            if(payload.notResolved().isEmpty()){
                authoritative = true;
            }

            if(!payload.notResolved().isEmpty()){
                logger.logTrace(PINK + "[TRACE] Could not resolve query: " + oopPacket.getQuestionSection(0).getNameString() +" from Zones, try Cache instead"+ RESET );
            }

            payload = resolutionPipeline.cacheLookUp(payload, oopPacket, id);

            if(payload.notResolved().isEmpty()){
                logger.logTrace(PINK + "[TRACE] Query: " +  oopPacket.getQuestionSection(0).getNameString() + " resolved from Cache"+ RESET);
                for (ResourceRecord answer: payload.answer()){
                    logger.logTrace(PINK + "[TRACE] Answer: " + String.join(".",answer.getName()) + "." + "of Type: " + answer.getType() + " with RData: " + answer.getRdata().toString() + RESET);
                }
                for (ResourceRecord authority: payload.authority()){
                    logger.logTrace(PINK + "[TRACE] Authority: " + String.join(".",authority.getName()) + "." + "of Type: " + authority.getType() + " with RData: " + authority.getRdata().toString() + RESET);
                }
                for (ResourceRecord additional: payload.additional()){
                    logger.logTrace(PINK + "[TRACE] Additional: " + String.join(".",additional.getName()) + "." + "of Type: " + additional.getType() + " with RData: " + additional.getRdata().toString() + RESET);
                }
            } else {
                logger.logTrace(PINK + "[TRACE] Query: " + oopPacket.getQuestionSection(0).getNameString() + " could not be resolved from Cache, try Resolver instead."+ RESET);
            }

        } else {
            // if Cache can't be reached
            for (QuestionSection request : oopPacket.getQuestionSections()) {
                payload.notResolved().add(request);

                logger.logTrace(PINK + "[TRACE] Query: " + request.getNameString() + " could not be resolved from Cache or Zone due to missing database connection, try Resolver instead."+ RESET);


                logger.log(
                        (DNSEvent.event(DNSEventType.CACHE_MISS, id, request.nameToString(), request.getQtype(), 0L, ResponseSource.CACHE,
                                DNS_SERVER.resolverOptions.strategy().toString(), "Local Cache", "127.0.0.1", 1, "testing is active, do not check cache", null).toHumanReadable()),
                        LoggingCategoryType.QUERIES,
                        LoggingSeverity.NOTICE
                );

            }
        }
        // Stub/RecursiveResolver should only be queried if there are queries not answered by the
        // cache and recursion is allowed
        if (DNS_SERVER.recursionAllowed && !payload.notResolved().isEmpty()) {
            payload = DNS_SERVER.queryResolver(payload, data);
        }

        logger.logTrace(PINK+ "[TRACE] Query: " + String.join(".", String.join(".",oopPacket.getQuestionSection(0).getName())+"." + " was successfully resolved" + RESET));
        for (ResourceRecord answer: payload.answer()){
            logger.logTrace(PINK + "[TRACE] Answer: " + String.join(".",answer.getName()) + "." + " of Type: " + answer.getType() + " with RData: " + answer.getRdata().toString() + RESET);
        }
        for (ResourceRecord authority: payload.authority()){
            logger.logTrace(PINK + "[TRACE] Authority: " + String.join(".",authority.getName()) + "." + " of Type: " + authority.getType() + " with RData: " + authority.getRdata().toString() + RESET);
        }
        for (ResourceRecord additional: payload.additional()){
            logger.logTrace(PINK + "[TRACE] Additional: " + String.join(".",additional.getName()) + "." + " of Type: " + additional.getType() + " with RData: " + additional.getRdata().toString() + RESET);
        }

        return responseBuilder.noError(payload, oopPacket, authoritative).toBytes();
    }
}
