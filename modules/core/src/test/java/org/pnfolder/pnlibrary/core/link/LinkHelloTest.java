package org.pnfolder.pnlibrary.core.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.link.wire.frame.HelloFrame;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.frame.LinkFrame;
import org.pnfolder.pnlibrary.core.link.wire.LinkHello;

class LinkHelloTest {
    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void helloLayoutNeverChanges() throws IOException {
        byte[] bytes = LinkCodec.encodeHello(new LinkHello("server", "1.2.3", "0.1.0-dev.9", NOW), key);

        // Every version reads every other's hello, so this layout is a promise: byte 1, time, role, profile, version, signature.
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        assertEquals(1, in.readUnsignedByte());
        assertEquals(NOW.toEpochMilli(), in.readLong());
        assertEquals("server", in.readUTF());
        assertEquals("1.2.3", in.readUTF());
        assertEquals("0.1.0-dev.9", in.readUTF());
        assertEquals(32, in.available());
    }

    @Test
    void helloIsReadBackAndRejectedWithAWrongKeyOrATooOldTime() {
        LinkHello hello = LinkHello.current(LinkHello.PROXY, "0.1.0-dev.9", NOW);
        byte[] bytes = LinkCodec.encodeHello(hello, key);

        LinkHello read = LinkCodec.decodeHello(bytes, key, NOW);
        assertNotNull(read);
        assertEquals(LinkCodec.PROFILE, read.getProfile());
        assertEquals("0.1.0-dev.9", read.getLibraryVersion());
        assertNull(LinkCodec.decodeHello(bytes, "another-key-another-key-another-k".getBytes(StandardCharsets.UTF_8), NOW));
        assertNull(LinkCodec.decodeHello(bytes, key, NOW.plusSeconds(600)));
    }

    @Test
    void helloArrivesAsAFrameLikeEveryOtherMessage() {
        byte[] bytes = LinkCodec.encodeHello(LinkHello.current(LinkHello.SERVER, "0.1.0-dev.9", NOW), key);

        LinkFrame frame = LinkCodec.decodeFrame(bytes, key, NOW);

        assertTrue(frame instanceof HelloFrame);
        assertEquals("0.1.0-dev.9", ((HelloFrame) frame).getHello().getLibraryVersion());
    }

    @Test
    void aPeerWithOtherFormatsIsReportedOnceAndForgottenWhenItMatchesAgain() {
        LinkPeers peers = new LinkPeers();
        LinkHello other = new LinkHello("server", "0.0.0", "0.1.0-dev.5", NOW);
        LinkHello same = LinkHello.current("server", "0.1.0-dev.9", NOW);

        String first = peers.meet("lobby", other, "0.1.0-dev.9");
        assertTrue(first.startsWith("lobby: версия связи 0.0.0 (pnLibrary 0.1.0-dev.5)"));
        assertNull(peers.meet("lobby", other, "0.1.0-dev.9"));
        assertEquals(1, peers.problems().size());
        assertTrue(peers.problems().get("lobby").contains("Обмен данными между ними не работает"));

        assertNotNull(peers.meet("lobby", same, "0.1.0-dev.9"));
        assertTrue(peers.problems().isEmpty());
        assertNull(peers.meet("lobby", same, "0.1.0-dev.9"));
    }

    @Test
    void theProxyWarnsAboutAServerWithOtherFormats() {
        Link link = new Link(key);
        List<String> warnings = new ArrayList<>();
        LinkReceiver receiver = new LinkReceiver(link, null, null, warnings::add, () -> "0.1.0-dev.9");

        assertTrue(receiver.receive("lobby", LinkCodec.encodeHello(new LinkHello("server", "0.0.0", "0.1.0-dev.5", Instant.now()), key)));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("lobby"));
        assertEquals(1, link.getPeers().problems().size());
    }
}
