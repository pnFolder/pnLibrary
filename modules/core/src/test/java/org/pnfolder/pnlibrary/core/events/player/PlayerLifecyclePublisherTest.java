package org.pnfolder.pnlibrary.core.events.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.Audiences;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.events.player.PlayerJoinedEvent;
import org.pnfolder.pnlibrary.api.events.player.PlayerLeftEvent;
import org.pnfolder.pnlibrary.api.events.player.PlayerLifecycleEvent;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.events.EventDispatcher;

class PlayerLifecyclePublisherTest {
    private final UUID id = UUID.randomUUID();

    private PnLibrary library(EventDispatcher events, OnlinePlayer player) {
        Audiences audiences = mock(Audiences.class);
        when(audiences.player(id)).thenReturn(player);
        PnLibrary library = mock(PnLibrary.class);
        when(library.getEvents()).thenReturn(events);
        when(library.getAudiences()).thenReturn(audiences);
        return library;
    }

    @Test
    void joinAndQuitReachPortableListenersWithTheResolvedPlayer() {
        OnlinePlayer player = mock(OnlinePlayer.class);
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> { });
        try {
            List<PlayerLifecycleEvent> seen = new ArrayList<>();
            events.scope(PluginId.of("test")).subscribe(PlayerLifecycleEvent.class, seen::add);
            PnLibrary library = library(events, player);

            PlayerLifecyclePublisher.joined(library, id, "Steve");
            PlayerLifecyclePublisher.left(library, id, "Steve");

            assertEquals(Arrays.asList(PlayerJoinedEvent.class, PlayerLeftEvent.class),
                    Arrays.asList(seen.get(0).getClass(), seen.get(1).getClass()));
            assertEquals("Steve", seen.get(0).getName());
            assertEquals("Steve", seen.get(1).getName());
            assertEquals(id, seen.get(0).getUniqueId());
            assertEquals(id, seen.get(1).getUniqueId());
            assertSame(player, seen.get(0).getPlayer());
        } finally {
            events.close();
        }
    }

    @Test
    void aPlayerThePlatformNoLongerKnowsIsAnnouncedWithoutAPlayerObject() {
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> { });
        try {
            AtomicReference<PlayerLeftEvent> seen = new AtomicReference<>();
            events.scope(PluginId.of("test")).subscribe(PlayerLeftEvent.class, seen::set);
            PlayerLifecyclePublisher.left(library(events, null), id, "Alex");
            assertEquals("Alex", seen.get().getName());
            assertNull(seen.get().getPlayer());
        } finally {
            events.close();
        }
    }

    @Test
    void aClosedEventServiceDropsTheAnnouncementInsteadOfFailingThePlatformListener() {
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> { });
        PnLibrary library = library(events, null);
        events.close();
        PlayerLifecyclePublisher.joined(library, id, "Steve");
    }
}
