package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SQLiteConnection {

    private static final String DEFAULT_DATABASE_URL =
            "jdbc:sqlite:aura-perfumes.db";

    private static String databaseUrl =
            DEFAULT_DATABASE_URL;

    public static void setDatabaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Database URL cannot be empty"
            );
        }

        databaseUrl = url;
    }

    public static void resetDatabaseUrl() {
        databaseUrl = DEFAULT_DATABASE_URL;
    }

    private SQLiteConnection() {
        // Utility class: no instances needed
    }

    public static Connection getConnection() throws SQLException {

        Connection connection =
                DriverManager.getConnection(databaseUrl);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }
}

