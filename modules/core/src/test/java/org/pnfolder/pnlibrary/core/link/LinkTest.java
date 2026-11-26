package org.pnfolder.pnlibrary.core.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.link.remote.LinkNames;
import org.pnfolder.pnlibrary.core.link.remote.LinkTransport;
import org.pnfolder.pnlibrary.core.link.remote.RemoteAction;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.link.wire.DataChunks;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkSecret;

class LinkTest {
    private final byte[] key = sequence();
    private final LinkReport report = new LinkReport("Paper 1.21", "0.0.1", 3,
        Arrays.asList(new LinkPlugin("shop", "1.0.0", "1.1.0", "UPDATE_AVAILABLE"),
            new LinkPlugin("pnlibrary", "0.0.1", null, "UP_TO_DATE")), Instant.now());
    private final RemoteRequest request = new RemoteRequest(UUID.randomUUID(), "games", "", "Admin", RemoteAction.UPDATE,
        "shop", Instant.now());
    private final DataCall call = new DataCall(UUID.randomUUID(), "games", "", "Admin", "snapshots",
        "{\"product\":\"shop\"}", Instant.now());

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

    private static final class Sent {
        final String server;
        final byte[] bytes;

        Sent(String server, byte[] bytes) {
            this.server = server;
            this.bytes = bytes;
        }
    }

    private static LinkTransport recording(List<Sent> sent, String unreachable) {
        return (server, bytes) -> {
            sent.add(new Sent(server, bytes));
            return !server.equals(unreachable);
        };
    }

    @Test
    void aSignedReportSurvivesTheTrip() {
        LinkReport decoded = LinkCodec.decode(LinkCodec.encode(report, key), key);

        LinkReport expected = new LinkReport(report.getPlatform(), report.getLibraryVersion(), report.getPlayers(),
            report.getPlugins(), report.getSentAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(expected, decoded);
    }

    @Test
    void aWrongKeyAChangedByteAndAnOldMessageAreAllDropped() {
        byte[] message = LinkCodec.encode(report, key);

        assertNull(LinkCodec.decode(message, filled(7)));
        byte[] changed = message.clone();
        changed[10] = (byte) (changed[10] + 1);
        assertNull(LinkCodec.decode(changed, key));
        assertNull(LinkCodec.decode(message, key, Instant.now().plus(Duration.ofMinutes(10))));
        assertNull(LinkCodec.decode(new byte[3], key));
    }

    @Test
    void onlyAValidReportFromAServerIsRemembered() {
        Link link = new Link(key);
        LinkRegistry registry = link.getRegistry();
        LinkReceiver receiver = new LinkReceiver(link);
        byte[] message = LinkCodec.encode(report, key);

        assertFalse(receiver.receive("lobby", false, message));
        byte[] broken = message.clone();
        broken[0] = 9;
        assertFalse(receiver.receive("lobby", true, broken));
        assertTrue(registry.all().isEmpty());
        assertTrue(receiver.receive("lobby", true, message));
        assertNotNull(registry.get("LOBBY"));
        assertFalse(new LinkReceiver(new Link(null)).receive("lobby", true, message));
    }

    @Test
    void theProxyMakesTheKeyOnceAndAServerOnlyReadsIt(@TempDir Path folder) {
        assertNull(LinkSecret.read(folder));
        byte[] made = LinkSecret.readOrCreate(folder);

        assertNotNull(made);
        assertTrue(Arrays.equals(made, LinkSecret.read(folder)));
        assertTrue(Arrays.equals(made, LinkSecret.readOrCreate(folder)));
    }

    @Test
    void theNetworkMapKeepsEveryServerAndDropsForgeries() {
        Set<String> servers = new LinkedHashSet<>(Collections.singletonList("lobby"));
        Set<String> plugins = new LinkedHashSet<>(Arrays.asList("shop", "chat"));
        ServerGroup group = new ServerGroup("grief", servers, plugins,
            Collections.singletonMap("lobby", Collections.singleton("quiz")),
            Collections.singletonMap("lobby", Collections.singleton("chat")));
        NetworkReport network = new NetworkReport(report,
            Arrays.asList(new NetworkServer("lobby", true, report), new NetworkServer("games", false, report)),
            report.getSentAt(), Collections.singletonList(group));
        byte[] message = LinkCodec.encodeNetwork(network, key);

        NetworkReport decoded = LinkCodec.decodeNetwork(message, key);
        assertEquals("lobby", decoded.getServers().get(0).getName());
        assertEquals("games", decoded.getServers().get(1).getName());
        assertTrue(decoded.getServers().get(0).getOnline());
        assertFalse(decoded.getServers().get(1).getOnline());
        assertEquals(report.getPlugins(), decoded.getServers().get(0).getReport().getPlugins());
        assertEquals(network.getGroups(), decoded.getGroups());
        assertNull(LinkCodec.decodeNetwork(message, filled(7)));
        assertNull(LinkCodec.decode(message, key));
        assertNull(LinkCodec.decodeNetwork(LinkCodec.encode(report, key), key));
    }

    @Test
    void requestsAndAnswersSurviveTheTripAndRejectForgeries() {
        RemoteRequest sent = LinkCodec.decodeRequest(LinkCodec.encodeRequest(request, key), key);
        assertEquals(request.getId(), sent.getId());
        assertEquals(RemoteAction.UPDATE, sent.getAction());
        assertEquals("shop", sent.getProduct());
        assertNull(LinkCodec.decodeRequest(LinkCodec.encodeRequest(request, key), filled(7)));

        RemoteResult result = new RemoteResult(request.getId(), "lobby", true, "Готово", Instant.now());
        RemoteResult answer = LinkCodec.decodeResult(LinkCodec.encodeResult(result, key), key);
        assertEquals("Готово", answer.getText());
        assertTrue(answer.getOk());
        assertNull(LinkCodec.decodeRequest(LinkCodec.encodeResult(result, key), key));
    }

    @Test
    void theProxyPassesARequestToItsTargetAndTheAnswerBackToWhoAsked() {
        List<Sent> sent = new ArrayList<>();
        LinkReceiver receiver = new LinkReceiver(new Link(key, null, recording(sent, "gone")));

        assertTrue(receiver.receive("lobby", true, LinkCodec.encodeRequest(request, key)));
        RemoteRequest forwarded = LinkCodec.decodeRequest(sent.get(0).bytes, key);
        assertEquals("games", sent.get(0).server);
        assertEquals("lobby", forwarded.getOrigin());

        sent.clear();
        RemoteResult reply = new RemoteResult(request.getId(), "lobby", true, "ok", Instant.now());
        assertTrue(receiver.receive("games", true, LinkCodec.encodeResult(reply, key)));
        assertEquals("lobby", sent.get(0).server);

        sent.clear();
        RemoteRequest toGone = new RemoteRequest(UUID.randomUUID(), "gone", request.getOrigin(), request.getActor(),
            request.getAction(), request.getProduct(), request.getSentAt());
        receiver.receive("lobby", true, LinkCodec.encodeRequest(toGone, key));
        Sent last = sent.get(sent.size() - 1);
        RemoteResult failure = LinkCodec.decodeResult(last.bytes, key);
        assertEquals("lobby", last.server);
        assertFalse(failure.getOk());
        assertFalse(receiver.receive("lobby", false, LinkCodec.encodeRequest(request, key)));
    }

    @Test
    void anAnswerToTheProxyItselfReachesWhoeverWaitsForIt() {
        Link link = new Link(key);
        AtomicReference<RemoteResult> got = new AtomicReference<>();
        link.getReplies().expect(request.getId(), got::set);
        LinkReceiver receiver = new LinkReceiver(link);

        receiver.receive("games", true, LinkCodec.encodeResult(
            new RemoteResult(request.getId(), LinkNames.PROXY_ORIGIN, true, "ok", Instant.now()), key));

        assertEquals("ok", got.get().getText());
    }

    @Test
    void aQuestionSurvivesTheTripAndRejectsForgeries() {
        DataCall decoded = LinkCodec.decodeCall(LinkCodec.encodeCall(call, key), key);
        assertEquals("snapshots", decoded.getMethod());
        assertEquals("{\"product\":\"shop\"}", decoded.getArgs());
        assertNull(LinkCodec.decodeCall(LinkCodec.encodeCall(call, key), filled(7)));
        assertNull(LinkCodec.decodeChunk(LinkCodec.encodeCall(call, key), key));
    }

    @Test
    void aLongAnswerTravelsInPiecesAndComesBackWhole() {
        Random random = new Random(1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 120_000; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        String text = builder.toString();
        List<byte[]> pieces = DataChunks.split(call.getId(), "lobby", true, text, key);

        assertTrue(pieces.size() > 1);
        List<DataChunk> chunks = new ArrayList<>();
        for (byte[] piece : pieces) {
            assertTrue(piece.length <= LinkCodec.MAX_FRAME_BYTES);
            chunks.add(LinkCodec.decodeChunk(piece, key));
        }
        Collections.sort(chunks, (a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        List<byte[]> bytes = new ArrayList<>();
        for (DataChunk chunk : chunks) {
            bytes.add(chunk.getBytes());
        }
        assertEquals(text, DataChunks.join(bytes));
        assertNull(LinkCodec.decodeChunk(pieces.get(0), filled(7)));
    }

    @Test
    void theAskerGetsTheAnswerWhenTheLastPieceArrivesAndAnErrorWhenTheServerRefuses() throws Exception {
        List<DataCall> asked = new ArrayList<>();
        DataClient client = new DataClient(key, bytes -> asked.add(LinkCodec.decodeCall(bytes, key)));
        CompletableFuture<String> good = client.call("games", "Admin", "version", "{}");
        CompletableFuture<String> bad = client.call("games", "Admin", "plan", "{}");
        assertFalse(good.isDone());

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            builder.append('x');
        }
        String text = builder.toString();
        List<DataChunk> pieces = new ArrayList<>();
        for (byte[] piece : DataChunks.split(asked.get(0).getId(), "lobby", true, text, key)) {
            pieces.add(LinkCodec.decodeChunk(piece, key));
        }
        for (DataChunk piece : pieces.subList(0, pieces.size() - 1)) {
            client.accept(piece);
        }
        assertFalse(good.isDone());
        client.accept(pieces.get(pieces.size() - 1));
        assertEquals(text, good.get());

        for (byte[] piece : DataChunks.split(asked.get(1).getId(), "lobby", false, "нет доступа", key)) {
            client.accept(LinkCodec.decodeChunk(piece, key));
        }
        assertTrue(bad.isCompletedExceptionally());
        ExecutionException failure = assertThrows(ExecutionException.class, bad::get);
        assertEquals("нет доступа", failure.getCause().getMessage());
    }

    @Test
    void theProxyPassesAQuestionOnTakesTheAnswerBackAndTellsTheAskerWhenTheServerIsGone() {
        List<Sent> sent = new ArrayList<>();
        LinkReceiver receiver = new LinkReceiver(new Link(key, null, recording(sent, "gone")));

        assertTrue(receiver.receive("lobby", true, LinkCodec.encodeCall(call, key)));
        assertEquals("games", sent.get(0).server);
        assertEquals("lobby", LinkCodec.decodeCall(sent.get(0).bytes, key).getOrigin());

        sent.clear();
        byte[] answer = DataChunks.split(call.getId(), "lobby", true, "[]", key).get(0);
        assertTrue(receiver.receive("games", true, answer));
        assertEquals("lobby", sent.get(0).server);

        sent.clear();
        DataCall toGone = new DataCall(call.getId(), "gone", call.getOrigin(), call.getViewer(), call.getMethod(),
            call.getArgs(), call.getSentAt());
        receiver.receive("lobby", true, LinkCodec.encodeCall(toGone, key));
        Sent last = sent.get(sent.size() - 1);
        DataChunk refused = LinkCodec.decodeChunk(last.bytes, key);
        assertEquals("lobby", last.server);
        assertFalse(refused.getOk());
        assertFalse(receiver.receive("lobby", false, LinkCodec.encodeCall(call, key)));
    }

    @Test
    void theProxyAnswersAQuestionForItselfThroughItsHandler() {
        List<Sent> sent = new ArrayList<>();
        LinkReceiver receiver = new LinkReceiver(new Link(key, null, recording(sent, "")), null,
            (question, reply) -> reply.accept(true, "answer to " + question.getMethod()));
        DataCall toProxy = new DataCall(call.getId(), LinkNames.PROXY, call.getOrigin(), call.getViewer(),
            call.getMethod(), call.getArgs(), call.getSentAt());

        receiver.receive("lobby", true, LinkCodec.encodeCall(toProxy, key));

        DataChunk chunk = LinkCodec.decodeChunk(sent.get(0).bytes, key);
        assertEquals("lobby", sent.get(0).server);
        assertEquals("answer to snapshots", DataChunks.join(Collections.singletonList(chunk.getBytes())));
    }
}
