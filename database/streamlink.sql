-- StreamLink database bootstrap
-- This script creates the required schema for a fresh local setup without resetting
-- an existing database. It is intentionally non-destructive: no DROP DATABASE,
-- TRUNCATE, or destructive reset statements are used.
--
-- Prerequisite: the root-level videos/ directory must contain all MP4 files that
-- are referenced here. The application validates these names and requires them to
-- resolve inside the server's videos/ directory.

CREATE DATABASE IF NOT EXISTS streamlink;
USE streamlink;

CREATE TABLE IF NOT EXISTS users (
    user_id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    category_id INT NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS videos (
    video_id INT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    file_path VARCHAR(255) NOT NULL,
    category_id INT DEFAULT NULL,
    uploaded_by INT DEFAULT NULL,
    PRIMARY KEY (video_id),
    KEY idx_videos_category_id (category_id),
    KEY idx_videos_uploaded_by (uploaded_by),
    CONSTRAINT fk_videos_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CONSTRAINT fk_videos_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS streams (
    stream_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    video_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (stream_id),
    KEY idx_streams_user_id (user_id),
    KEY idx_streams_video_id (video_id),
    CONSTRAINT fk_streams_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_streams_video
        FOREIGN KEY (video_id) REFERENCES videos(video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the categories used by the GUI and the category search flow.
INSERT INTO categories (category_name)
SELECT 'Movies'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Movies');

INSERT INTO categories (category_name)
SELECT 'Education'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Education');

INSERT INTO categories (category_name)
SELECT 'Music'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Music');

INSERT INTO categories (category_name)
SELECT 'Sports'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Sports');

-- Seed the known video metadata. The application expects a file name directly in
-- the server videos/ directory (for example: sample.mp4), not a traversal path.
-- The current code also tolerates a legacy videos/sample.mp4 form, but the seed
-- here stays in the canonical bare-filename form for compatibility.
INSERT INTO videos (title, description, file_path, category_id, uploaded_by)
SELECT 'Sample Video',
       'Test video for StreamLink',
       'sample.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Movies'),
       (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1
    FROM videos
    WHERE title = 'Sample Video' AND file_path = 'sample.mp4'
);

INSERT INTO videos (title, description, file_path, category_id, uploaded_by)
SELECT 'Java Tutorial',
       'Java tutorial video',
       'java_tutorial.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Education'),
       (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1
    FROM videos
    WHERE title = 'Java Tutorial' AND file_path = 'java_tutorial.mp4'
);

INSERT INTO videos (title, description, file_path, category_id, uploaded_by)
SELECT 'Music Video',
       'Music video',
       'music_video.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Music'),
       (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1
    FROM videos
    WHERE title = 'Music Video' AND file_path = 'music_video.mp4'
);

INSERT INTO videos (title, description, file_path, category_id, uploaded_by)
SELECT 'Football',
       'Football video',
       'football.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Sports'),
       (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1
    FROM videos
    WHERE title = 'Football' AND file_path = 'football.mp4'
);

-- The SQL script does not delete existing users or stream history. If the schema is
-- already present and contains data, rerunning this script only inserts missing
-- category and video records and leaves the rest of the database unchanged.
