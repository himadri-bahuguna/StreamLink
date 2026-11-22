package com.streamlink.client;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.streamlink.client.model.Category;
import com.streamlink.client.model.Dashboard;
import com.streamlink.client.model.HistoryEntry;
import com.streamlink.client.model.Page;
import com.streamlink.client.model.UserSession;
import com.streamlink.client.model.Video;
import com.streamlink.common.Config;
import com.streamlink.common.Protocol;

/**
 * Client side of the StreamLink protocol. Every method opens one TCP
 * connection, sends one request and reads the reply, with connect and read
 * timeouts so a stopped or frozen server never hangs the caller.
 *
 * <p>Methods block, so the GUI only calls them from background threads.</p>
 */
public final class ServerApi {

    private static final int CONNECT_TIMEOUT_MILLIS = 4_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int TRANSFER_READ_TIMEOUT_MILLIS = 30_000;

    private final String host;
    private final int port;

    public record DownloadedVideo(File file, int streamId) {
}

    public ServerApi() {
        this(Config.serverHost(), Config.serverPort());
    }

    public ServerApi(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String address() {
        return host + ":" + port;
    }

    // ------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------

    public long ping() throws ApiException {
        Reply reply = call(Protocol.PING);
        return parseLong(reply.header(2), 0);
    }

    public UserSession login(String username, String password) throws ApiException {
        Reply reply = call(Protocol.AUTH, username, password);
        return new UserSession(reply.header(1), reply.header(2), reply.header(3));
    }

    public void register(String username, String password) throws ApiException {
        call(Protocol.REGISTER, username, password);
    }

    public void logout(UserSession session) {
        if (session == null) {
            return;
        }
        try {
            call(Protocol.SIGN_OUT, session.token());
        } catch (ApiException ignored) {
            // Best effort: the session also expires on the server by itself.
        }
    }

    public List<Category> categories(UserSession session) throws ApiException {
        Reply reply = call(Protocol.CATEGORIES, token(session));
        List<Category> categories = new ArrayList<>();
        for (String[] row : reply.rows()) {
            categories.add(new Category(parseInt(row[1], 0), row[2], parseInt(row[3], 0)));
        }
        return categories;
    }

    public Page<Video> browse(UserSession session, String query, String category,
            String sort, int page, int pageSize) throws ApiException {
        Reply reply = call(Protocol.BROWSE, token(session),
                query == null ? "" : query, category == null ? "" : category,
                sort == null ? "" : sort, page, pageSize);
        List<Video> videos = new ArrayList<>();
        for (String[] row : reply.rows()) {
            videos.add(parseVideo(row));
        }
        return new Page<>(videos, parseInt(reply.header(1), videos.size()),
                parseInt(reply.header(2), page), parseInt(reply.header(3), pageSize));
    }

    public List<Video> recent(UserSession session, int limit) throws ApiException {
        Reply reply = call(Protocol.RECENT, token(session), limit);
        List<Video> videos = new ArrayList<>();
        for (String[] row : reply.rows()) {
            videos.add(parseVideo(row));
        }
        return videos;
    }

    public Page<HistoryEntry> history(UserSession session, int page, int pageSize)
            throws ApiException {
        Reply reply = call(Protocol.HISTORY, token(session), page, pageSize);
        List<HistoryEntry> entries = new ArrayList<>();
        for (String[] row : reply.rows()) {
            entries.add(new HistoryEntry(
                    parseInt(row[1], 0), parseInt(row[2], 0), row[3], row[4], row[5],
                    "1".equals(row[6]), parseTime(row[7]), parseTime(row[8]), row[9]));
        }
        return new Page<>(entries, parseInt(reply.header(1), entries.size()),
                parseInt(reply.header(2), page), parseInt(reply.header(3), pageSize));
    }

    public Dashboard dashboard(UserSession session) throws ApiException {
        Reply reply = call(Protocol.ADMIN_DASHBOARD, token(session));
        Map<String, Long> summary = new LinkedHashMap<>();
        Map<String, Long> status = new LinkedHashMap<>();
        List<Dashboard.TopVideo> top = new ArrayList<>();
        List<Dashboard.DailyCount> daily = new ArrayList<>();
        List<Dashboard.Activity> recent = new ArrayList<>();
        List<Dashboard.LiveStream> live = new ArrayList<>();

        for (String[] row : reply.rows()) {
            if (row.length < 2) {
                continue;
            }
            switch (row[1]) {
                case "SUMMARY" -> summary.put(row[2], parseLong(row[3], 0));
                case "STATUS" -> status.put(row[2], parseLong(row[3], 0));
                case "TOP" -> top.add(new Dashboard.TopVideo(
                        parseInt(row[2], 0), row[3], parseInt(row[4], 0)));
                case "DAILY" -> daily.add(new Dashboard.DailyCount(
                        LocalDate.parse(row[2]), parseInt(row[3], 0)));
                case "RECENT" -> recent.add(new Dashboard.Activity(
                        parseInt(row[2], 0), row[3], row[4],
                        parseTime(row[5]), parseTime(row[6]), row[7]));
                case "LIVE" -> live.add(new Dashboard.LiveStream(
                        parseInt(row[2], 0), row[3], row[4], parseLong(row[5], 0), row[6]));
                default -> {
                    // ignore sections this client does not know yet
                }
            }
        }
        return new Dashboard(summary, status, top, daily, recent, live);
    }

    /**
     * Downloads a video into a temporary file.
     *
     * @param socketSink receives the open socket so the caller can close it to
     *                   cancel a transfer that is blocked in a read
     */
   /**
 * Existing download method.
 *
 * Used by the CLI and ConcurrencyDemo. It releases the server slot
 * immediately after the file has downloaded.
 */
public File download(UserSession session, Video video,
        TransferProgressListener progress, BooleanSupplier cancelled,
        Consumer<Socket> socketSink) throws ApiException {

    DownloadedVideo downloaded = downloadForPlayback(
            session, video, progress, cancelled, socketSink);

    if (downloaded.streamId() > 0) {
        stopStream(session, downloaded.streamId());
    }

    return downloaded.file();
}

/**
 * Downloads a video but keeps its server-side stream reservation active.
 *
 * The JavaFX player should use this method. It must call stopStream()
 * when the player window closes.
 */
public DownloadedVideo downloadForPlayback(UserSession session, Video video,
        TransferProgressListener progress, BooleanSupplier cancelled,
        Consumer<Socket> socketSink) throws ApiException {

    if (video.fileName() == null || video.fileName().isBlank()) {
        throw new ApiException(
                Protocol.E_INVALID,
                "This video has no playable file.");
    }

    try (Socket socket = open(TRANSFER_READ_TIMEOUT_MILLIS)) {

        if (socketSink != null) {
            socketSink.accept(socket);
        }

        PrintWriter output = writer(socket);

        output.println("START_STREAM_AUTH:" + token(session) + ":"
                + video.id() + ":" + video.fileName());

        if (output.checkError()) {
            throw new IOException("Could not send the stream request.");
        }

        BufferedInputStream input =
                new BufferedInputStream(socket.getInputStream());

        VideoStreamReceiver.ReceivedVideo received =
                VideoStreamReceiver.receiveWithStreamId(
                        input, progress, cancelled);

        return new DownloadedVideo(
                received.file(), received.streamId());

    } catch (ApiException e) {
        throw e;

    } catch (IOException e) {

        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new ApiException(
                    ApiException.CANCELLED,
                    "Download cancelled.", e);
        }

        if (e instanceof SocketTimeoutException) {
            throw new ApiException(
                    ApiException.TIMEOUT,
                    "The server stopped sending data. Try again.", e);
        }

        String message = e.getMessage() == null
                ? "Transfer failed." : e.getMessage();

        if (message.contains("Maximum number of active streams")) {
            throw new ApiException(
                    Protocol.E_BUSY,
                    "All streaming slots on the server are in use right now. "
                            + "Try again when another stream finishes.", e);
        }

        if (message.contains("Invalid or expired session")) {
            throw new ApiException(
                    Protocol.E_SESSION,
                    "Your session has expired. Please sign in again.", e);
        }

        if (message.startsWith("Connection reset")
                || message.startsWith("Connection closed")
                || message.startsWith("Socket closed")) {
            throw new ApiException(
                    ApiException.OFFLINE,
                    "The connection to the server was lost during the transfer.",
                    e);
        }

        throw new ApiException(Protocol.E_INTERNAL, message, e);
    }
}

/**
 * Tells the server that the user has closed the video player.
 *
 * A short retry handles the race where the client finishes receiving
 * the file just before the server marks the stream as PLAYING.
 */
public void stopStream(UserSession session, int streamId)
        throws ApiException {

    if (streamId <= 0) {
        return;
    }

    String sessionToken = token(session);
    String lastError = "Could not stop the stream.";

    for (int attempt = 1; attempt <= 10; attempt++) {

        try (Socket socket = open(READ_TIMEOUT_MILLIS)) {

            PrintWriter output = writer(socket);

            output.println("STOP_STREAM:" + sessionToken + ":" + streamId);

            if (output.checkError()) {
                throw new IOException("Could not send STOP_STREAM request.");
            }

            BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));

            String response = input.readLine();

            if (response == null) {
                throw new IOException(
                        "Server closed the connection without confirming STOP_STREAM.");
            }

            if (response.equals("STOP_STREAM:OK")) {
                return;
            }

            String prefix = "STOP_STREAM:ERROR MESSAGE:";

            if (response.startsWith(prefix)) {
                lastError = response.substring(prefix.length());

                if (lastError.contains("Transfer is still active")
                        || lastError.contains("Database finalization failed")) {
                    Thread.sleep(150);
                    continue;
                }

                throw new ApiException(
                        Protocol.E_INTERNAL, lastError);
            }

            throw new ApiException(
                    ApiException.PROTOCOL,
                    "Unexpected STOP_STREAM response: " + response);

        } catch (ApiException e) {
            throw e;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    ApiException.CANCELLED,
                    "Stream stop request was interrupted.", e);

        } catch (IOException e) {
            lastError = e.getMessage() == null
                    ? "Could not contact the server to stop the stream."
                    : e.getMessage();

        }
    }

    throw new ApiException(Protocol.E_INTERNAL, lastError);
}

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private Reply call(Object... request) throws ApiException {
        try (Socket socket = open(READ_TIMEOUT_MILLIS)) {
            PrintWriter output = writer(socket);
            output.println(Protocol.encode(request));
            if (output.checkError()) {
                throw new IOException("Could not send the request.");
            }
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));

            String headerLine = input.readLine();
            if (headerLine == null) {
                throw new ApiException(ApiException.PROTOCOL,
                        "The server closed the connection without answering.");
            }
            String[] header = Protocol.decode(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = input.readLine()) != null && !Protocol.END.equals(line)) {
                String[] row = Protocol.decode(line);
                if (row.length > 0 && Protocol.ROW.equals(row[0])) {
                    rows.add(row);
                }
            }
            if (line == null) {
                throw new ApiException(ApiException.PROTOCOL,
                        "The server reply was cut off. Try again.");
            }
            if (header.length > 0 && Protocol.ERR.equals(header[0])) {
                throw new ApiException(header.length > 1 ? header[1] : Protocol.E_INTERNAL,
                        header.length > 2 ? header[2] : "The server reported an error.");
            }
            if (header.length == 0 || !Protocol.OK.equals(header[0])) {
                throw new ApiException(ApiException.PROTOCOL,
                        "Unexpected reply from the server.");
            }
            return new Reply(header, rows);
        } catch (ApiException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new ApiException(ApiException.TIMEOUT,
                    "The server at " + address() + " did not answer in time.", e);
        } catch (InterruptedIOException e) {
            throw new ApiException(ApiException.CANCELLED, "Request cancelled.", e);
        } catch (IOException e) {
            throw new ApiException(ApiException.OFFLINE,
                    "Can't reach the StreamLink server at " + address()
                            + ". Make sure StreamServer is running.", e);
        }
    }

    private Socket open(int readTimeoutMillis) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(readTimeoutMillis);
            return socket;
        } catch (ConnectException | UnknownHostException | SocketTimeoutException e) {
            socket.close();
            throw new ApiException(ApiException.OFFLINE,
                    "Can't reach the StreamLink server at " + address()
                            + ". Make sure StreamServer is running.", e);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static PrintWriter writer(Socket socket) throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private static String token(UserSession session) throws ApiException {
        if (session == null || session.token() == null || session.token().isBlank()) {
            throw new ApiException(Protocol.E_SESSION, "Please sign in first.");
        }
        return session.token();
    }

    private static Video parseVideo(String[] row) {
        return new Video(
                parseInt(row[1], 0), row[2], row[3], row[4], row[5], row[6],
                parseTime(row[7]), parseInt(row[8], 0), "1".equals(row[9]),
                row.length > 10 ? parseTime(row[10]) : null);
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private record Reply(String[] header, List<String[]> rows) {
        String header(int index) {
            return index < header.length ? header[index] : "";
        }
    }
}
