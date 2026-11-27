package org.pnfolder.pnlibrary.core.link.wire;

import org.pnfolder.pnlibrary.core.link.wire.frame.CallFrame;
import org.pnfolder.pnlibrary.core.link.wire.frame.ChunkFrame;
import org.pnfolder.pnlibrary.core.link.wire.frame.LinkFrame;
import org.pnfolder.pnlibrary.core.link.wire.frame.ReportFrame;
import org.pnfolder.pnlibrary.core.link.wire.frame.RequestFrame;
import org.pnfolder.pnlibrary.core.link.wire.frame.ResultFrame;
import org.pnfolder.pnlibrary.core.link.wire.seal.CachedHmac;
import org.pnfolder.pnlibrary.core.link.wire.seal.FrameSigner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.link.ServerGroup;

class LinkFrameTest {
    private final byte[] key = sequence();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private final UUID id = new UUID(1, 2);
    private final LinkReport report = new LinkReport("Paper", "0.1.0", 4,
        Collections.singletonList(new LinkPlugin("shop", "1.0", "1.1", "UPDATE_AVAILABLE")), now);
    private final RemoteRequest request = new RemoteRequest(id, "lobby", "survival", "admin", RemoteAction.CHECK, null, now);
    private final RemoteResult result = new RemoteResult(id, "survival", true, "done", now);
    private final DataCall call = new DataCall(id, "lobby", "survival", "admin", "plugins", "{}", now);
    private final DataChunk chunk = new DataChunk(id, "survival", true, 0, 1, new byte[] {1, 2, 3}, now);

    private static byte[] sequence() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static byte[] filled(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private List<byte[]> valid() {
        return Arrays.asList(LinkCodec.encode(report, key), LinkCodec.encodeRequest(request, key),
            LinkCodec.encodeResult(result, key), LinkCodec.encodeCall(call, key), LinkCodec.encodeChunk(chunk, key));
    }

    private static String show(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof DataChunk) {
            DataChunk c = (DataChunk) value;
            return "chunk(" + c.getId() + "," + c.getTo() + "," + c.getOk() + "," + c.getIndex() + "," + c.getCount() + ","
                + Arrays.toString(c.getBytes()) + "," + c.getSentAt() + ")";
        }
        return value.toString();
    }

    /** The receiver's previous chain: one signature check per decoder until one accepts. */
    private String chain(byte[] message, Instant at) {
        Object decoded = LinkCodec.decode(message, key, at);
        if (decoded == null) {
            decoded = LinkCodec.decodeRequest(message, key, at);
        }
        if (decoded == null) {
            decoded = LinkCodec.decodeResult(message, key, at);
        }
        if (decoded == null) {
            decoded = LinkCodec.decodeCall(message, key, at);
        }
        if (decoded == null) {
            decoded = LinkCodec.decodeChunk(message, key, at);
        }
        return show(decoded);
    }

    private String frame(byte[] message, Instant at) {
        LinkFrame decoded = LinkCodec.decodeFrame(message, key, at, new CachedHmac());
        Object value = null;
        if (decoded instanceof ReportFrame) {
            value = ((ReportFrame) decoded).getReport();
        } else if (decoded instanceof RequestFrame) {
            value = ((RequestFrame) decoded).getRequest();
        } else if (decoded instanceof ResultFrame) {
            value = ((ResultFrame) decoded).getResult();
        } else if (decoded instanceof CallFrame) {
            value = ((CallFrame) decoded).getCall();
        } else if (decoded instanceof ChunkFrame) {
            value = ((ChunkFrame) decoded).getChunk();
        }
        return show(value);
    }

    private static byte[] bumped(byte[] message, int index) {
        byte[] copy = message.clone();
        copy[index] = (byte) (copy[index] + 1);
        return copy;
    }

    /** Valid frames of every kind, forged, foreign, stale, cut and extended ones. */
    private List<byte[]> corpus() {
        List<byte[]> frames = valid();
        List<byte[]> all = new ArrayList<>(frames);
        for (byte[] message : frames) {
            all.add(bumped(message, 0));
            all.add(bumped(message, 5));
            all.add(bumped(message, message.length - 1));
            all.add(Arrays.copyOf(message, message.length - 1));
            all.add(Arrays.copyOf(message, 40));
            all.add(Arrays.copyOf(message, message.length + 1));
            all.add(new byte[0]);
        }
        byte[] other = filled(9);
        all.add(LinkCodec.encode(report, other));
        all.add(LinkCodec.encodeRequest(request, other));
        all.add(LinkCodec.encodeChunk(chunk, other));
        all.add(LinkCodec.encodeNetwork(new NetworkReport(report, Collections.emptyList(), now,
            Collections.emptyList()), key));
        all.add(LinkCodec.encodeChunk(new DataChunk(id, "x", true, 5, 2, new byte[0], now), key));
        return all;
    }

    @Test
    void oneFrameDecoderAnswersExactlyAsTheChainOfFiveDecodersDid() {
        for (Instant at : Arrays.asList(now, now.plus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(1)))) {
            List<byte[]> corpus = corpus();
            for (int index = 0; index < corpus.size(); index++) {
                assertEquals(chain(corpus.get(index), at), frame(corpus.get(index), at), "frame " + index + " at " + at);
            }
        }
        for (byte[] message : valid()) {
            assertNotNull(LinkCodec.decodeFrame(message, key, now));
        }
        assertNull(LinkCodec.decodeFrame(valid().get(0), filled(7), now));
    }

    @Test
    void aFrameOfAnyKindCostsOneSignatureCheckWhereTheChainCostUpToFive() {
        AtomicInteger checks = new AtomicInteger();
        CachedHmac real = new CachedHmac();
        FrameSigner counting = (bytes, signKey) -> {
            checks.incrementAndGet();
            return real.sign(bytes, signKey);
        };
        for (byte[] message : valid()) {
            checks.set(0);
            assertNotNull(LinkCodec.decodeFrame(message, key, now, counting));
            assertEquals(1, checks.get());
        }
    }

    @Test
    void aThreadGetsOneMacPerKeyHoweverManyFramesItSigns() {
        AtomicInteger created = new AtomicInteger();
        CachedHmac signer = new CachedHmac(() -> {
            created.incrementAndGet();
            try {
                return Mac.getInstance("HmacSHA256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        });
        byte[] message = new byte[100];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) i;
        }
        byte[] expected = new CachedHmac().sign(message, key);
        for (int i = 0; i < 100; i++) {
            assertEquals(Arrays.toString(expected), Arrays.toString(signer.sign(message, key)));
        }
        assertEquals(1, created.get());
        signer.sign(message, filled(5));
        assertEquals(2, created.get());
    }
}
