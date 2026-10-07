package com.streamlink.database;

public class VideoTest {

    public static void main(String[] args) {

        VideoDAO videoDAO = new VideoDAO();

        videoDAO.showAllVideos();
    }
}