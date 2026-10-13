package com.streamlink.database;

import java.sql.Connection;
 import java.sql.PreparedStatement;
 import java.sql.ResultSet;
 import java.sql.SQLException;

public class UserDAO {

public Integer authenticate(String username, String password) {
String sql = "SELECT user_id, password FROM users WHERE username = ?";
try (Connection connection = DBConnection.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
statement.setString(1, username);
try (ResultSet result = statement.executeQuery()) {
if (result.next() && password.equals(result.getString("password"))) {
return result.getInt("user_id");
}
return null;
}
} catch (SQLException e) {
System.out.println("Login error: " + e.getMessage());
return null;
}
}

public boolean login(String username, String password) { String sql = "SELECT password FROM users WHERE username = ?";

try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

statement.setString(1, username);

try (ResultSet result = statement.executeQuery()) { if (result.next()) { String storedPassword = result.getString("password"); return password.equals(storedPassword); } return false; }

} catch (SQLException e) { System.out.println("Login error: " + e.getMessage()); return false; } } }