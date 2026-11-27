package org.pnfolder.pnlibrary.core.network.backend;

import net.kyori.adventure.key.Key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;

class BackendChannelsTest {
    private final List<Key> registered = new ArrayList<>();
    private final List<Key> unregistered = new ArrayList<>();
    private final List<byte[]> sent = new ArrayList<>();
    private boolean playerOnline;
    private final List<String> failures = new ArrayList<>();
    private final BackendChannels channels = new BackendChannels(registered::add, unregistered::add, (name, data) -> {
        if (!playerOnline) {
            return false;
        }
        sent.add(data);
        return true;
    }, (message, error) -> failures.add(message));

    @Test
    void sendsAtOnceWhenAPlayerIsOnline() {
        playerOnline = true;
        CompletableFuture<Boolean> result = channels.channel(Key.key("my:main")).send(new byte[] {1}).toCompletableFuture();
        assertTrue(result.isDone() && result.join());
        assertEquals(1, sent.size());
    }

    @Test
    void waitsForAPlayerAndSendsInOrderOnFlush() {
        ServerChannel channel = channels.channel(Key.key("my:main"));
        CompletableFuture<Boolean> first = channel.send(new byte[] {1}).toCompletableFuture();
        CompletableFuture<Boolean> second = channel.send(new byte[] {2}).toCompletableFuture();
        assertFalse(first.isDone());
        playerOnline = true;
        channels.flush();
        assertTrue(first.join() && second.join());
        assertEquals(1, sent.get(0)[0]);
        assertEquals(2, sent.get(1)[0]);
    }

    @Test
    void dropsTheOldestWhenTheQueueIsFull() {
        ServerChannel channel = channels.channel(Key.key("my:main"));
        CompletableFuture<Boolean> oldest = channel.send(new byte[] {0}).toCompletableFuture();
        for (int i = 0; i < BackendChannel.QUEUE_LIMIT; i++) {
            channel.send(new byte[] {1});
        }
        assertTrue(oldest.isDone());
        assertFalse(oldest.join());
    }

    @Test
    void closeFailsTheWaitingAndUnregisters() {
        ServerChannel channel = channels.channel(Key.key("my:main"));
        CompletableFuture<Boolean> waiting = channel.send(new byte[] {1}).toCompletableFuture();
        channel.close();
        assertFalse(waiting.join());
        assertEquals(1, unregistered.size());
        assertThrows(IllegalStateException.class, () -> channel.send(new byte[0]));
    }

    @Test
    void deliversMessagesFromTheProxyAndSurvivesAFailingListener() {
        ServerChannel channel = channels.channel(Key.key("my:main"));
        List<byte[]> got = new ArrayList<>();
        channel.listen(data -> {
            throw new IllegalStateException("boom");
        });
        channel.listen(got::add);
        assertTrue(channels.dispatch("my:main", new byte[] {9}));
        assertEquals(1, got.size());
        assertEquals(java.util.Collections.singletonList("A listener of channel my:main failed"), failures);
        assertFalse(channels.dispatch("other:x", new byte[0]));
        assertEquals(1, registered.size());
    }
}
