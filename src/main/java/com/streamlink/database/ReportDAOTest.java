package com.streamlink.database;

import java.sql.Connection;
import java.sql.SQLException;

public class ReportDAOTest {

    public static void main(String[] args) {

        try (Connection connection = DBConnection.getConnection()) {

            ReportDAO reportDAO = new ReportDAO(connection);

            System.out.println("===== STREAMS PER VIDEO =====");
            for (ReportDAO.VideoStreamCount video :
                    reportDAO.getStreamsPerVideo()) {
                System.out.println(video);
            }

            System.out.println("\n===== MOST WATCHED VIDEOS =====");
            for (ReportDAO.VideoStreamCount video :
                    reportDAO.getMostWatched()) {
                System.out.println(video);
            }

        } catch (SQLException e) {
            System.out.println("Report test failed: " + e.getMessage());
        }
    }
}