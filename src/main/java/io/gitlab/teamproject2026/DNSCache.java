package io.gitlab.teamproject2026;

import java.sql.*;


public class DNSCache{
    public  Boolean connect(){
        try {Class.forName("org.postgresql.Driver");}
        catch(ClassNotFoundException e) {System.out.println(e);}
        try{
            Connection db = DriverManager.getConnection("jdbc:postgresql://localhost:5432/dns", "postgres", "example");
            return true;
        } catch (SQLException e ){
            System.out.println(e);
            return false;
        }
    }
}