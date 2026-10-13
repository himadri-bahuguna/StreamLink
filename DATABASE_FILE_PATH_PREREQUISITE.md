# Database prerequisite for GUI playback

The Java code now reads `videos.file_path` when returning video records. The
existing MySQL database must be updated before database-backed listing or GUI
playback can work:

```sql
ALTER TABLE videos
    ADD COLUMN file_path VARCHAR(255) NOT NULL;
```

Populate each row with a path or filename that points to an MP4 directly inside
the server's `videos` directory, for example:

```sql
UPDATE videos SET file_path = 'sample.mp4' WHERE video_id = 1;
```

The server validates this value and sends only the validated filename in the
list response. It does not allow paths outside `videos`.

This project does not execute the schema change or provide a migration.
