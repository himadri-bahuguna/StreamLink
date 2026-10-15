# StreamLink database setup and file-path prerequisite

This project reads `videos.file_path` when listing videos and when validating a
stream request before sending the actual MP4 bytes. The application validates the
stored path and allows only files that resolve inside the server's root-level
`videos/` directory.

The canonical, compatible representation is a bare filename such as:

- `sample.mp4`
- `java_tutorial.mp4`
- `music_video.mp4`
- `football.mp4`

A legacy prefixed form such as `videos/sample.mp4` is also accepted by the
current `FileManager` path logic, but the database seed data should prefer the
bare filename format to stay directly compatible with the server-side validation
boundary.

## MySQL setup

1. Create the database and schema using the canonical script:

```bash
mysql -u root -p < database/streamlink.sql
```

or import it from MySQL Workbench.

2. Set the password used by the application:

```bash
export STREAMLINK_DB_PASSWORD="your_mysql_password"
```

On Windows PowerShell:

```powershell
$env:STREAMLINK_DB_PASSWORD = "your_mysql_password"
```

3. Confirm the seeded categories and video records:

```sql
USE streamlink;
SELECT category_id, category_name FROM categories ORDER BY category_id;
SELECT video_id, title, file_path FROM videos ORDER BY video_id;
```

The expected MP4 filenames are:

- sample.mp4
- java_tutorial.mp4
- music_video.mp4
- football.mp4

The project root must contain a `videos/` directory with those files present.

## Required file layout

```text
StreamLink/
├─ videos/
│  ├─ sample.mp4
│  ├─ java_tutorial.mp4
│  ├─ music_video.mp4
│  └─ football.mp4
├─ database/
│  └─ streamlink.sql
└─ src/
```

## Startup and verification

To start the server locally:

```bash
mvn exec:java -Dexec.mainClass="com.streamlink.server.StreamServer"
```

To launch the GUI:

```bash
mvn javafx:run -Djavafx.mainClass=com.streamlink.client.StreamLinkGUI
```


If a database connection fails, check the MySQL service is running and that
`STREAMLINK_DB_PASSWORD` is set correctly. Missing or wrong `file_path` values
cause the server to report a clear validation error before the transfer starts.

## Important security note

The server rejects path traversal attempts such as `../outside.mp4` and only
allows files that resolve inside the `videos/` directory. Do not trust arbitrary
paths from database records or client input.
