package org.pnfolder.pnlibrary.core.link;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkSecret;

class LinkLimitsTest {
    private final byte[] key = sequence(32, 1);

    private static byte[] sequence(int size, int step) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i * step);
        }
        return bytes;
    }

    private static byte[] filled(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static String repeat(String text, int times) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < times; i++) {
            result.append(text);
        }
        return result.toString();
    }

    private static LinkReport report(int plugins) {
        List<LinkPlugin> list = new ArrayList<LinkPlugin>();
        for (int i = 0; i < plugins; i++) {
            list.add(new LinkPlugin(repeat("p", 150) + i, "1.0.0", "2.0.0", "UPDATE_AVAILABLE"));
        }
        return new LinkReport("Paper", "0.1.0", 1, list, Instant.now());
    }

    @Test
    void aHugeNetworkMapIsCutToTheFrameLimitAndStillVerifies() {
        List<NetworkServer> servers = new ArrayList<NetworkServer>();
        List<ServerGroup> groups = new ArrayList<ServerGroup>();
        for (int i = 0; i < 30; i++) {
            servers.add(new NetworkServer("s" + i, true, report(40)));
            Set<String> groupServers = new LinkedHashSet<String>();
            Set<String> plugins = new LinkedHashSet<String>();
            for (int n = 0; n < 60; n++) {
                groupServers.add(repeat("server", 30) + n);
                plugins.add(repeat("plugin", 30) + n);
            }
            groups.add(new ServerGroup("g" + i, groupServers, plugins));
        }
        NetworkReport network = new NetworkReport(report(40), servers, Instant.now(), groups);

        byte[] message = LinkCodec.encodeNetwork(network, key);

        assertTrue(message.length <= LinkCodec.MAX_FRAME_BYTES, "size " + message.length);
        assertNotNull(LinkCodec.decodeNetwork(message, key));
        assertNull(LinkCodec.decodeNetwork(message, filled(32, 9)));
    }

    @Test
    void anyChangedByteOfASignedFrameIsRejected() {
        byte[] message = LinkCodec.encode(report(2), key);
        for (int index = 0; index < message.length; index += 7) {
            byte[] forged = message.clone();
            forged[index] = (byte) (forged[index] ^ 1);
            assertNull(LinkCodec.decode(forged, key), "byte " + index);
        }
        byte[] longer = Arrays.copyOf(message, message.length + 1);
        assertNull(LinkCodec.decode(longer, key));
    }

    @Test
    void anExistingUnreadableKeyFileIsKeptAndNotReplaced(@TempDir Path folder) throws Exception {
        Path file = folder.resolve(LinkSecret.FILE);
        Files.write(file, "not a key !!".getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();

        assertNull(LinkSecret.readOrCreate(folder, warnings::add));

        assertEquals("not a key !!", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        assertEquals(1, warnings.size());
    }

    @Test
    void aKeyFileWithABomAndALineBreakIsRead(@TempDir Path folder) throws Exception {
        byte[] key = sequence(32, 3);
        Files.write(folder.resolve(LinkSecret.FILE),
            ("﻿" + Base64.getEncoder().encodeToString(key) + "\r\n").getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(key, LinkSecret.readOrCreate(folder));
    }

    @Test
    void aMissingKeyFileIsCreatedInAMissingFolder(@TempDir Path folder) {
        Path nested = folder.resolve("a/b");

        byte[] made = LinkSecret.readOrCreate(nested);

        assertNotNull(made);
        assertTrue(Files.exists(nested.resolve(LinkSecret.FILE)));
    }
}
