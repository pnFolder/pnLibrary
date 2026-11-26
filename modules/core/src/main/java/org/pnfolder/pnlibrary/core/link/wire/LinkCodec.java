package org.pnfolder.pnlibrary.core.link.wire;

import java.time.Duration;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;

/**
 * Message format: body + HMAC-SHA256(body, sharedKey). A wrong signature, a bad version or a stale time means the
 * message is dropped, so a player sending raw bytes on the channel cannot pose as a server.
 */
public final class LinkCodec {
    public static final String CHANNEL = "pnlibrary:link";
    public static final int MAX_PLUGINS = 150;
    public static final int MAX_NETWORK_PLUGINS = 40;
    public static final int MAX_FRAME_BYTES = 32_000;
    public static final int MAX_SERVERS = 30;

    static final int MAX_TEXT = 200;

    static final int MAX_GROUPS = 30;
    static final int MAX_GROUP_ITEMS = 60;
    private static final int VERSION = 7;
    private static final int NETWORK_VERSION = 8;
    private static final int REQUEST_VERSION = 3;
    private static final int RESULT_VERSION = 4;
    private static final int CALL_VERSION = 5;
    private static final int CHUNK_VERSION = 6;
    private static final Duration TOLERANCE = Duration.ofMinutes(2);
    private static final FrameSigner HMAC = new CachedHmac();

    private LinkCodec() {
    }

    public static @NotNull byte[] encode(@NotNull LinkReport report, @NotNull byte[] key) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(VERSION);
            ReportWire.write(out, report, MAX_PLUGINS);
        });
    }

    public static @Nullable LinkReport decode(@NotNull byte[] message, @NotNull byte[] key) {
        return decode(message, key, Instant.now());
    }

    public static @Nullable LinkReport decode(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC, in -> {
            if (in.readUnsignedByte() != VERSION) {
                return null;
            }
            LinkReport report = ReportWire.read(in);
            return fresh(report.getSentAt(), now) ? report : null;
        });
    }

    // Servers kick the player who delivers an oversized plugin message, so we halve limits until it fits.
    public static @NotNull byte[] encodeNetwork(@NotNull NetworkReport network, @NotNull byte[] key) {
        int plugins = MAX_NETWORK_PLUGINS;
        int items = MAX_GROUP_ITEMS;
        int servers = MAX_SERVERS;
        int groups = MAX_GROUPS;
        while (true) {
            byte[] bytes = networkBytes(network, key, plugins, items, servers, groups);
            if (bytes.length <= MAX_FRAME_BYTES || (plugins == 0 && items == 0 && servers == 0 && groups == 0)) {
                return bytes;
            }
            if (plugins > 0) {
                plugins /= 2;
            } else if (items > 0) {
                items /= 2;
            } else {
                servers /= 2;
                groups /= 2;
            }
        }
    }

    private static byte[] networkBytes(NetworkReport network, byte[] key, int plugins, int items, int servers, int groups) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(NETWORK_VERSION);
            NetworkWire.write(out, network, plugins, items, servers, groups);
        });
    }

    public static @Nullable NetworkReport decodeNetwork(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeNetwork(message, key, Instant.now());
    }

    public static @Nullable NetworkReport decodeNetwork(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC, in -> {
            if (in.readUnsignedByte() != NETWORK_VERSION) {
                return null;
            }
            return NetworkWire.read(in, now);
        });
    }

    public static @NotNull byte[] encodeRequest(@NotNull RemoteRequest request, @NotNull byte[] key) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(REQUEST_VERSION);
            RemoteWire.writeRequest(out, request);
        });
    }

    public static @Nullable RemoteRequest decodeRequest(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeRequest(message, key, Instant.now());
    }

    public static @Nullable RemoteRequest decodeRequest(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC,
            in -> in.readUnsignedByte() == REQUEST_VERSION ? RemoteWire.readRequest(in, now) : null);
    }

    public static @NotNull byte[] encodeResult(@NotNull RemoteResult result, @NotNull byte[] key) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(RESULT_VERSION);
            RemoteWire.writeResult(out, result);
        });
    }

    public static @Nullable RemoteResult decodeResult(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeResult(message, key, Instant.now());
    }

    public static @Nullable RemoteResult decodeResult(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC,
            in -> in.readUnsignedByte() == RESULT_VERSION ? RemoteWire.readResult(in, now) : null);
    }

    public static @NotNull byte[] encodeCall(@NotNull DataCall call, @NotNull byte[] key) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(CALL_VERSION);
            DataWire.writeCall(out, call);
        });
    }

    public static @Nullable DataCall decodeCall(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeCall(message, key, Instant.now());
    }

    public static @Nullable DataCall decodeCall(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC,
            in -> in.readUnsignedByte() == CALL_VERSION ? DataWire.readCall(in, now) : null);
    }

    public static @NotNull byte[] encodeChunk(@NotNull DataChunk chunk, @NotNull byte[] key) {
        return FrameSeal.signed(HMAC, key, out -> {
            out.writeByte(CHUNK_VERSION);
            DataWire.writeChunk(out, chunk);
        });
    }

    public static @Nullable DataChunk decodeChunk(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeChunk(message, key, Instant.now());
    }

    public static @Nullable DataChunk decodeChunk(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return FrameSeal.verified(message, key, HMAC,
            in -> in.readUnsignedByte() == CHUNK_VERSION ? DataWire.readChunk(in, now) : null);
    }

    public static @Nullable LinkFrame decodeFrame(@NotNull byte[] message, @NotNull byte[] key) {
        return decodeFrame(message, key, Instant.now(), HMAC);
    }

    public static @Nullable LinkFrame decodeFrame(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now) {
        return decodeFrame(message, key, now, HMAC);
    }

    /** Decodes any frame a game server may send; {@code signer} is the signature check, replaceable in tests. */
    public static @Nullable LinkFrame decodeFrame(@NotNull byte[] message, @NotNull byte[] key, @NotNull Instant now,
                                                  @NotNull FrameSigner signer) {
        return FrameSeal.verified(message, key, signer, in -> {
            int version = in.readUnsignedByte();
            if (version == VERSION) {
                LinkReport report = ReportWire.read(in);
                return fresh(report.getSentAt(), now) ? new ReportFrame(report) : null;
            }
            if (version == REQUEST_VERSION) {
                RemoteRequest request = RemoteWire.readRequest(in, now);
                return request == null ? null : new RequestFrame(request);
            }
            if (version == RESULT_VERSION) {
                RemoteResult result = RemoteWire.readResult(in, now);
                return result == null ? null : new ResultFrame(result);
            }
            if (version == CALL_VERSION) {
                DataCall call = DataWire.readCall(in, now);
                return call == null ? null : new CallFrame(call);
            }
            if (version == CHUNK_VERSION) {
                DataChunk chunk = DataWire.readChunk(in, now);
                return chunk == null ? null : new ChunkFrame(chunk);
            }
            return null;
        });
    }

    static boolean fresh(Instant sentAt, Instant now) {
        return Math.abs(Duration.between(sentAt, now).toMillis()) <= TOLERANCE.toMillis();
    }
}
