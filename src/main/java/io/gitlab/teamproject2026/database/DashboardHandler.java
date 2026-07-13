package io.gitlab.teamproject2026.database;

import io.gitlab.teamproject2026.logging.DNSEvent;
import io.gitlab.teamproject2026.model.*;
import io.gitlab.teamproject2026.model.rdata.RData;
import io.gitlab.teamproject2026.model.rdata.*;
import io.gitlab.teamproject2026.server.DNSPayload;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class DashboardHandler {
    private final Connection conn;
    private static final String SQL_QUESTION_INSERT =
            "INSERT INTO dashboard_queries (id, qType, domain, rCode) VALUES (?, ?, ?, ?)";
    private static final String SQL_RESULT_INSERT =
            "INSERT INTO dashboard_responses (id, result, rType, servedBy, locatedIn) VALUES (?, ?, ?, ?, ?)";

    public DashboardHandler(DatabaseConnectionProvider provider) {
        this.conn = provider.getConnection();
    }

    public enum ResponseOrigin {
        ZONE,
        CACHE,
        FORWARDER,
        RECURSIVE_RESOLVER,
        STUB;
    }
    public void storeDNSResult(String id, DNSPayload resolvedRecords, ResponseOrigin responseOrigin){
        storeDNSRecords(id, resolvedRecords.answer(), responseOrigin, "ANSWER_SECTION");
        storeDNSRecords(id, resolvedRecords.authority(), responseOrigin, "AUTHORITY_SECTION");
        storeDNSRecords(id, resolvedRecords.additional(), responseOrigin, "ADDITIONAL_SECTION");
    }
    private void storeDNSRecords(String id, List<ResourceRecord> resolvedRecords, ResponseOrigin responseOrigin, String locatedIn){
        try{
            PreparedStatement stmt = conn.prepareStatement(SQL_RESULT_INSERT);
            for(ResourceRecord record : resolvedRecords){
                switch (record.getRdata()) {
                    case RDataA data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getAddress()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataAAAA data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getAddress()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataCNAME data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getCname()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataMX data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getExchange()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataNS data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getNsdname()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataPTR data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getPtrdname()), record.getType(), responseOrigin, locatedIn);
                        break;
                    case RDataTXT data:
                        fillResultStatement(stmt, id, Arrays.toString(data.getTxtdata()), record.getType(), responseOrigin, locatedIn);
                        break;
                    default:
                        fillResultStatement(stmt, id, "", record.getType(), responseOrigin, locatedIn);
                }

            }
            stmt.executeBatch();
        } catch (SQLException _){

        }

    }

    public void storeDNSMetadata(String id, DNSPacket packet){
        try{
            storeQuestionSection(packet.getQuestionSection(0), id, packet.getRcode());
        } catch (SQLException _){

        }
    }

    private void storeQuestionSection(QuestionSection section, String id, int rCode) throws SQLException{
        PreparedStatement stmt = conn.prepareStatement(SQL_QUESTION_INSERT);
        stmt.setString(1, id);
        stmt.setString(2, RRType.mapToString(section.getQtype()));
        stmt.setString(3, section.getNameString());
        stmt.setString(4, RCode.mapToString(rCode));
    }

    private void fillResultStatement (PreparedStatement stmt, String id, String result, int rType, ResponseOrigin servedBy, String locatedIn) throws SQLException{
        stmt.setString(1, id);
        stmt.setString(2, result);
        stmt.setString(3, RRType.mapToString(rType));
        stmt.setString(4, servedBy.toString());
        stmt.setString(5, locatedIn);
        stmt.addBatch();
    }
}
