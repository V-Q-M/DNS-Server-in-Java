package io.gitlab.teamproject2026;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;


public class DNSCacheSetupTest extends AbstractDNSCacheTests {

    Connection connection = null;
    
    void establishConnection(){
        try {
            connection = connectionProvider.getConnection();
        } catch (SQLException e) {
            System.out.println(e);
        } catch (ClassNotFoundException f) {
            System.out.println(f + "This shouldn't happen, perhaps the dependencies aren't configured correctly?");
        }
    }

    void setUpContainer(){
        
        try {
            Statement statement = connection.createStatement();
            statement.addBatch("CREATE DOMAIN uint AS bigint CHECK(VALUE >= 0 AND VALUE < 2^32)");
            statement.addBatch("CREATE DOMAIN usmallint AS int CHECK(VALUE >= 0 AND VALUE < 2^16)");
            statement.addBatch("CREATE TYPE RRTypeEnum AS ENUM ('A','AAAA','SOA','TXT','MX', 'PTR', 'NS','CNAME')");
            statement.addBatch("CREATE TYPE RRClassEnum AS ENUM ('IN')");
            statement.addBatch("CREATE TABLE ResourceRecord (name text, rrtype RRTypeEnum,rrclass RRClassEnum,TTL uint, rdlength usmallint)");
            statement.addBatch("CREATE TABLE ns_rdata (nsdname text) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE cname_rdata (cname text) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE a_rdata (ipv4 bytea CHECK (octet_length(ipv4)=4)) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE aaaa_rdata (ipv6 bytea CHECK (octet_length(ipv6)=16)) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE ptr_rdata (ptrdname text) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE txt_rdata (txtdata text) INHERITS (ResourceRecord)");
            statement.addBatch("CREATE TABLE mx_rdata (pref usmallint,exchange text) INHERITS (ResourceRecord);");
            statement.addBatch("CREATE TABLE soa_rdata (mname text,rname text,serial uint,refresh uint,retry uint,expire uint,minimum uint) INHERITS (ResourceRecord)");
            statement.executeBatch();
        } catch (SQLException e) {
            System.out.println(e);
        }
    }

    @Test
    void CacheSetupTest() {
        try {
            establishConnection();
            setUpContainer();
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet results = metaData.getTables(null,null,null,new String[] {"TABLE"});
            assertTrue(results.first());
        } catch (SQLException e) {
            System.err.println(e);
            assertTrue(false,e.toString());
        }
    }
}
