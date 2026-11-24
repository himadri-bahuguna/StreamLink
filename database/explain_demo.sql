-- StreamLink: DBMS demonstration queries for the final evaluation.
-- Run in MySQL Workbench or: mysql -u root -p streamlink < database/explain_demo.sql
USE streamlink;

-- 1. Index use: filtering videos by category uses idx_videos_category_id.
--    Look at the "key" column of the EXPLAIN output.
EXPLAIN
SELECT video_id, title
FROM videos
WHERE category_id = 1;

-- 2. Watch history: WHERE user_id = ? ORDER BY start_time DESC.
--    With idx_streams_user_start (user_id, start_time) MySQL reads the rows
--    already in order; "Extra" should NOT say "Using filesort".
EXPLAIN
SELECT s.stream_id, v.title, s.start_time, s.end_time, s.status
FROM streams s
JOIN videos v ON v.video_id = s.video_id
WHERE s.user_id = 1
ORDER BY s.start_time DESC
LIMIT 15 OFFSET 0;

-- 3. Search with a leading wildcard cannot use a B-tree index on title
--    ("type" = ALL). That is expected for LIKE '%text%'; a FULLTEXT index
--    would be the next step for a large catalogue.
EXPLAIN
SELECT video_id, title
FROM videos
WHERE title LIKE '%java%';

-- 4. Pagination: page 2 with 6 videos per page, ordered by title.
SELECT v.video_id, v.title, c.category_name
FROM videos v
LEFT JOIN categories c ON v.category_id = c.category_id
ORDER BY v.title
LIMIT 6 OFFSET 6;

-- 5. Reporting: most watched videos, including videos with no streams.
SELECT v.title, COUNT(s.stream_id) AS total_streams
FROM videos v
LEFT JOIN streams s ON v.video_id = s.video_id
GROUP BY v.video_id, v.title
ORDER BY total_streams DESC, v.title;

-- 6. Stream outcomes (the dashboard's "How streams ended").
SELECT status, COUNT(*) AS total
FROM streams
GROUP BY status;

-- 7. Transaction demo: the INSERT is invisible to other sessions until COMMIT,
--    and ROLLBACK undoes it. (Uses the first user and video.)
START TRANSACTION;
INSERT INTO streams (user_id, video_id, status)
SELECT (SELECT MIN(user_id) FROM users), (SELECT MIN(video_id) FROM videos), 'ACTIVE';
SELECT COUNT(*) AS rows_inside_transaction FROM streams;
ROLLBACK;
SELECT COUNT(*) AS rows_after_rollback FROM streams;
