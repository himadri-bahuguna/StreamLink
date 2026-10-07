package com.streamlink.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class VideoDAO {

    public void showAllVideos() {

        String query = """
                SELECT
                    v.video_id,
                    v.title,
                    v.description,
                    c.category_name,
                    u.username
                FROM videos v
                JOIN categories c
                    ON v.category_id = c.category_id
                JOIN users u
                    ON v.uploaded_by = u.user_id
                """;

        try (
            Connection connection = DBConnection.getConnection();
            PreparedStatement statement =
                    connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery()
        ) {

            System.out.println("\n===== Available Videos =====");

            while (resultSet.next()) {

                System.out.println(
                        "ID: " + resultSet.getInt("video_id")
                );

                System.out.println(
                        "Title: " + resultSet.getString("title")
                );

                System.out.println(
                        "Description: " +
                        resultSet.getString("description")
                );

                System.out.println(
                        "Category: " +
                        resultSet.getString("category_name")
                );

                System.out.println(
                        "Uploaded By: " +
                        resultSet.getString("username")
                );

                System.out.println("----------------------------");
            }

        } catch (Exception e) {

            System.out.println("Error retrieving videos.");
            e.printStackTrace();
        }
    }
}