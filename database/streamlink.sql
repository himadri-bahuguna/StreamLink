
CREATE DATABASE IF NOT EXISTS streamlink;
USE streamlink;

CREATE TABLE IF NOT EXISTS users (
    user_id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    category_id INT NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (category_id),
    UNIQUE KEY uq_categories_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS videos (
    video_id INT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    file_path VARCHAR(255) NOT NULL,
    category_id INT DEFAULT NULL,
    uploaded_by INT DEFAULT NULL,
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    status ENUM('ACTIVE','COMPLETED','INTERRUPTED') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (stream_id),
    KEY idx_streams_user_id (user_id),
    KEY idx_streams_video_id (video_id),
    KEY idx_streams_user_start (user_id, start_time),
    CONSTRAINT fk_streams_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_streams_video
        FOREIGN KEY (video_id) REFERENCES videos(video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Categories used by the GUI filters and home-screen rows.
INSERT INTO categories (category_name)
SELECT 'Movies' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Movies');

INSERT INTO categories (category_name)
SELECT 'Education' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Education');

INSERT INTO categories (category_name)
SELECT 'Music' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Music');

INSERT INTO categories (category_name)
SELECT 'Sports' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Sports');

INSERT INTO categories (category_name)
SELECT 'Documentary' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_name = 'Documentary');

-- Video metadata. file_path is a bare file name inside videos/.
-- uploaded_by is left NULL: the catalogue shows these as uploaded by "StreamLink".
INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Sample Video',
       'A short sample used to check that streaming works end to end.',
       'sample.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Movies')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'sample.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Midnight Platform',
       'A night-time short set on an empty railway platform. Demo placeholder clip.',
       'midnight_platform.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Movies')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'midnight_platform.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Paper Lanterns',
       'A festival evening told through lantern light. Demo placeholder clip.',
       'paper_lanterns.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Movies')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'paper_lanterns.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Java Tutorial',
       'Java basics: classes, objects and the main method.',
       'java_tutorial.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Education')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'java_tutorial.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Sockets in Ten Minutes',
       'How a TCP client and server exchange messages, step by step.',
       'sockets_intro.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Education')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'sockets_intro.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'SQL Joins Explained',
       'INNER, LEFT and RIGHT joins with small example tables.',
       'sql_joins.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Education')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'sql_joins.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Threads and Locks',
       'Race conditions, critical sections and how synchronized protects shared data.',
       'threads_locks.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Education')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'threads_locks.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Music Video',
       'Music video.',
       'music_video.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Music')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'music_video.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Lo-fi Study Session',
       'Calm background music for long study evenings. Demo placeholder clip.',
       'lofi_session.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Music')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'lofi_session.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Football',
       'Football video.',
       'football.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Sports')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'football.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Cricket Highlights',
       'The best overs from the inter-department final. Demo placeholder clip.',
       'cricket_highlights.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Sports')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'cricket_highlights.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Campus Marathon',
       'Runners, routes and the finish line of the annual campus run. Demo placeholder clip.',
       'campus_marathon.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Sports')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'campus_marathon.mp4');

INSERT INTO videos (title, description, file_path, category_id)
SELECT 'Himalayan Rivers',
       'Where the rivers of Uttarakhand begin. Demo placeholder clip.',
       'himalayan_rivers.mp4',
       (SELECT category_id FROM categories WHERE category_name = 'Documentary')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE file_path = 'himalayan_rivers.mp4');
