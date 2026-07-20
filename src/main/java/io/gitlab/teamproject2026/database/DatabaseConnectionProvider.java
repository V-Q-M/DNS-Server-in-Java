package io.gitlab.teamproject2026.database;

import java.sql.*;

public class DatabaseConnectionProvider {

    private String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/dns");
    private String user = System.getenv().getOrDefault("DB_USER", "postgres");
    private String password = System.getenv().getOrDefault("DB_PASSWORD", "example");
    private final Connection dbConnection;

    public DatabaseConnectionProvider(String url, String user, String password)
            throws SQLException, ClassNotFoundException {
        this.password = password;
        this.url = url;
        this.user = user;
        this.dbConnection = createConnection();
    }

    public DatabaseConnectionProvider() throws SQLException, ClassNotFoundException {
        this.dbConnection = createConnection();
    }

    /**
     * @return A Connection to an SQL Database, using the url, user and password defined at object creation
     * @throws SQLException if url cannot be parser
     * @throws ClassNotFoundException if Postgresql driver cannot be found
     */
    private Connection createConnection() throws SQLException, ClassNotFoundException {
        Connection db = null;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println(e);
            throw e;
        }
        try {
            db = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            System.out.println(e);
            throw e;
        }

        return db;
    }

    /** @return The connection created in the contructor */
    public Connection getConnection() {
        return dbConnection;
    }

    /**
     * Opens a new connection using the same url/user/password. Used by handlers that must not share a connection
     * with other handlers on other threads. Currently used by DashboardHandler, because using the same connection
     * as CacheHandler caused problems
     */
    public Connection newConnection() throws SQLException, ClassNotFoundException {
        return createConnection();
    }
}
