
CREATE DATABASE IF NOT EXISTS streamlink;
USE streamlink;

-- 1. Users table
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- 2. Categories table
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- 3. Videos table
CREATE TABLE videos (
    video_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    file_path VARCHAR(255) NOT NULL,
    category_id INT NOT NULL,
    uploaded_by INT NULL,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_videos_category (category_id),
    INDEX idx_videos_uploaded_by (uploaded_by),

    FOREIGN KEY (category_id)
        REFERENCES categories(category_id),

    FOREIGN KEY (uploaded_by)
        REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 4. Streams table
CREATE TABLE streams (
    stream_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    video_id INT NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL DEFAULT NULL,

    INDEX idx_streams_user (user_id),
    INDEX idx_streams_video (video_id),

    FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    FOREIGN KEY (video_id)
        REFERENCES videos(video_id)
) ENGINE=InnoDB;
