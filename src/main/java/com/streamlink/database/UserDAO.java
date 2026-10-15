
package com.streamlink.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.mindrot.jbcrypt.BCrypt;

public class UserDAO {

    public Integer authenticate(String username, String password) {
        String sql = "SELECT user_id, password FROM users WHERE username = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String storedPassword = result.getString("password");

                    if (verifyPassword(password, storedPassword)) {
                        return result.getInt("user_id");
                    }
                }

                return null;
            }

        } catch (SQLException e) {
            System.out.println("Login error: " + e.getMessage());
            return null;
        }
    }

    public boolean login(String username, String password) {
        return authenticate(username, password) != null;
    }

    private boolean verifyPassword(String password, String storedPassword) {
        if (storedPassword == null || password == null) {
            return false;
        }

        // If the password is already hashed, verify the BCrypt hash.
        if (storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(password, storedPassword);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // Support existing plain-text passwords and upgrade them after login.
        if (password.equals(storedPassword)) {
            updatePasswordHash(storedPassword, password);
            return true;
        }

        return false;
    }

    private void updatePasswordHash(String oldPassword, String password) {
        String sql = "UPDATE users SET password = ? WHERE password = ?";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            String hashedPassword = BCrypt.hashpw(
                    password, BCrypt.gensalt(12));

            statement.setString(1, hashedPassword);
            statement.setString(2, oldPassword);
            statement.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Password hash update error: " + e.getMessage());
        }
    }
}
