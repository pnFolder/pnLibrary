package org.pnfolder.pnlibrary.core.runtime.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole;

class PlatformAudiencesTest {
    @Test
    void lookupsDelegateWhileCloseSuppressesStaleResolution() {
        Sender console = new Sender("console", new ArrayList<>(), false);
        Player player = new Player(UUID.randomUUID(), "Alice", new ArrayList<>(), false);
        RecordingAdapter adapter = new RecordingAdapter(console, new ArrayList<>(Arrays.asList(player)));
        PlatformAudiences service = new PlatformAudiences(adapter);

        assertSame(adapter.console(), service.console());
        assertSame(player, service.player(player.getUniqueId()));
        assertSame(player, service.of("native"));
        service.close();
        assertNull(service.player(player.getUniqueId()));
        assertNull(service.of("native"));
        assertEquals(Collections.emptyList(), service.players());
    }

    @Test
    void aPlayerIsFoundByNameIgnoringCase() {
        Player player = new Player(UUID.randomUUID(), "Alice", new ArrayList<>(), false);
        PlatformAudiences service = new PlatformAudiences(
            new RecordingAdapter(new Sender("console", new ArrayList<>(), false), new ArrayList<>(Arrays.asList(player))));

        assertSame(player, service.player("aLiCe"));
        assertNull(service.player("Bob"));
    }

    @Test
    void dynamicAndCompositeAudiencesDeduplicateIsolateFailuresAndPlaySoundForEach() {
        List<String> calls = new ArrayList<>();
        Player first = new Player(UUID.randomUUID(), "first", calls, false);
        Player broken = new Player(UUID.randomUUID(), "broken", calls, true);
        Player last = new Player(UUID.randomUUID(), "last", calls, false);
        List<OnlinePlayer> members = new ArrayList<>(Arrays.asList(first, broken, first, last));
        PlatformAudiences service = new PlatformAudiences(new RecordingAdapter(new Sender("console", new ArrayList<>(), false), members));

        service.sendMessage(Component.text("hello"));
        service.playSound(Sound.sound(Key.key("test", "sound"), Sound.Source.MASTER, 1f, 1f));

        assertEquals(Arrays.asList("first:message", "broken:message", "last:message", "first:sound", "broken:sound", "last:sound"), calls);

        members.add(new Player(UUID.randomUUID(), "NowOnline", calls, false));
        service.sendActionBar(Component.text("bar"));
        assertEquals("NowOnline:bar", calls.get(calls.size() - 1));
    }

    @Test
    void aReceiverThatThrowsIsReportedOnceAndTheOthersStillGetTheMessage() {
        List<String> calls = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        List<OnlinePlayer> members = new ArrayList<>(Arrays.asList(
            new Player(UUID.randomUUID(), "broken", calls, true), new Player(UUID.randomUUID(), "last", calls, false)));
        PlatformAudiences service = new PlatformAudiences(new RecordingAdapter(new Sender("console", new ArrayList<>(), false), members),
            failures::add);

        for (int i = 0; i < 2; i++) {
            service.sendMessage(Component.text("hello"));
        }

        assertEquals(1, failures.size());
        assertEquals(Arrays.asList("broken:message", "last:message", "broken:message", "last:message"), calls);
    }

    @Test
    void theAudiencesFollowWhoIsOnlineAndCanBeFiltered() {
        List<String> calls = new ArrayList<>();
        List<OnlinePlayer> members = new ArrayList<>(Arrays.asList(
            new Player(UUID.randomUUID(), "Alice", calls, false), new Player(UUID.randomUUID(), "Bob", calls, false)));
        PlatformAudiences service = new PlatformAudiences(new RecordingAdapter(new Sender("console", calls, false), members));

        service.filterAudience(it -> AudienceExtensions.name(it).equals("Bob")).sendActionBar(Component.text("bar"));
        service.sendActionBar(Component.text("bar"));

        assertEquals(Arrays.asList("Bob:bar", "console:bar", "Alice:bar", "Bob:bar"), calls);
    }

    private static class Sender implements Audience {
        private final String name;
        private final List<String> calls;
        private final boolean failMessages;

        private Sender(String name, List<String> calls, boolean failMessages) {
            this.name = name;
            this.calls = calls;
            this.failMessages = failMessages;
        }

        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers(name, this instanceof OnlinePlayer ? ((OnlinePlayer) this).getUniqueId() : null,
                permission -> true);
        }

        @Override
        public void sendMessage(@NotNull Component text) {
            calls.add(name + ":message");
            if (failMessages) {
                throw new IllegalStateException("delivery");
            }
        }

        @Override
        public void sendActionBar(@NotNull Component text) {
            calls.add(name + ":bar");
        }

        @Override
        public void playSound(@NotNull Sound sound) {
            calls.add(name + ":sound");
        }
    }

    private static final class Player extends Sender implements OnlinePlayer {
        private final UUID uniqueId;

        private Player(UUID uniqueId, String name, List<String> calls, boolean failMessages) {
            super(name, calls, failMessages);
            this.uniqueId = uniqueId;
        }

        @Override
        public @NotNull UUID getUniqueId() {
            return uniqueId;
        }

        @Override
        public @NotNull Pointers pointers() {
            return super.pointers();
        }
    }

    private static final class RecordingAdapter implements PlatformAudienceAdapter {
        private final Console console;
        private final List<OnlinePlayer> players;

        private RecordingAdapter(Audience console, List<OnlinePlayer> players) {
            this.console = new PlatformConsole(console);
            this.players = players;
        }

        @Override
        public @NotNull Console console() {
            return console;
        }

        @Override
        public @Nullable OnlinePlayer player(@NotNull UUID uniqueId) {
            for (OnlinePlayer player : players) {
                if (player.getUniqueId().equals(uniqueId)) {
                    return player;
                }
            }
            return null;
        }

        @Override
        public @Nullable Audience of(@NotNull Object nativeSender) {
            return players.isEmpty() ? null : players.get(0);
        }

        @Override
        public @NotNull List<OnlinePlayer> players() {
            return new ArrayList<>(players);
        }
    }
}
