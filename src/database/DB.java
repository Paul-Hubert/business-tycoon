package database;

import java.sql.*;

/**
 * Thread-safe JDBC connection factory.
 * Creates a new connection per call — suitable for multi-threaded use.
 * Use in try-with-resources to ensure connections are closed:
 *
 *   try (Connection conn = DB.connect()) { ... }
 */
public class DB {

    private DB() {}

    public static Connection connect() throws SQLException {
        try {
            Class.forName(Provider.DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MariaDB driver not found on classpath", e);
        }
        return DriverManager.getConnection(Provider.CONNECTION_URL, Provider.USERNAME, Provider.PASSWORD);
    }
}
