# Stream lifecycle

Authenticated GUI playback uses a short session handshake:

```text
LOGIN:<username>:<password>
AUTH:OK TOKEN:<server-issued-token>
```

The token is then used with the selected database video ID and validated
filename:

```text
START_STREAM_AUTH:<token>:<video_id>:<filename>
```

The server validates the token, confirms that the video ID and stored
`file_path` resolve to the requested filename, inserts a row into `streams`,
and retains the generated `stream_id`. The database transaction is committed
before binary transfer begins. The row receives `end_time` after transfer
success, transfer failure, or client disconnect.

An explicit stop request is:

```text
STOP_STREAM:<token>:<stream_id>
```

The server checks token ownership. If the transfer is still active, the
request is rejected without changing the database row or releasing the
reservation. After transfer completion, the request finalizes the matching
record idempotently. If database finalization fails, the stream remains
tracked and its reservation remains held so a later stop/finalization retry
can reconcile the same `stream_id`. The existing `START_STREAM:<filename>` request remains
available for unauthenticated test and legacy clients, but it does not create
database stream records.

The current client downloads the complete file before opening `VideoPlayer`.
Consequently, stopping local playback does not cancel a transfer that has
already completed. `STOP_STREAM` is available for server-side lifecycle
finalization, but mid-transfer cancellation would require a separate control
channel.

The login handshake currently uses the existing plaintext TCP connection
protocol. Production deployment should protect the connection with TLS before
using this mechanism over an untrusted network.
