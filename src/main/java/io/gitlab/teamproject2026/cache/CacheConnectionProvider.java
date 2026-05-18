package io.gitlab.teamproject2026.cache;

import java.sql.*;

public class CacheConnectionProvider{
    private String url = "jdbc:postgresql://localhost:5432/dns";
    private String user = "postgres";
    private String password = "example";


    public CacheConnectionProvider(String url, String user, String password){
        this.password=password;
        this.url=url;
        this.user=user;
    }

    public CacheConnectionProvider(){}

    /**
     * 
     * @return A Connection to an SQL Database, using the url, user and password defined at object creation
     * @throws SQLException if url cannot be parser
     * @throws ClassNotFoundException if Postgresql driver cannot be found
     */
    public  Connection getConnection() throws SQLException,ClassNotFoundException{
        Connection db = null;
        try {Class.forName("org.postgresql.Driver");}
        catch(ClassNotFoundException e) {
            System.out.println(e);
            throw e;
        }
        try{
            db = DriverManager.getConnection(url, user, password);
        } catch (SQLException e ){
            System.out.println(e);
            throw e;
        }
        
        return db;
    }
}