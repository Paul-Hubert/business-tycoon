package database;

public interface Provider {
    String DRIVER="org.mariadb.jdbc.Driver";

    // Use localhost for local testing; Docker uses 'mariadb' hostname
    String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "db";
    String CONNECTION_URL="jdbc:mariadb://" + DB_HOST + "/" + DB_NAME;

    String USERNAME="user";
    String PASSWORD="password";
}
