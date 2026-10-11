package com.streamlink.database;

import java.sql.Connection;
 import java.sql.PreparedStatement;
 import java.sql.ResultSet;
 import java.sql.SQLException;

public class UserDAO {

public boolean login(String username, String password) { String sql = "SELECT password FROM users WHERE username = ?";

try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

statement.setString(1, username);

try (ResultSet result = statement.executeQuery()) { if (result.next()) { String storedPassword = result.getString("password"); return password.equals(storedPassword); } return false; }

} catch (SQLException e) { System.out.println("Login error: " + e.getMessage()); return false; } } }