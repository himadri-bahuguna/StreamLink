
package com.streamlink.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/streamlink";

    private static final String USER = "root";

    private static final String PASSWORD =
            System.getenv("STREAMLINK_DB_PASSWORD");

    public static Connection getConnection() throws SQLException {

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException(
                    "Database password is not configured. "
                    + "Set the STREAMLINK_DB_PASSWORD environment variable."
            );
        }

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
