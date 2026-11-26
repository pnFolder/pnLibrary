package org.pnfolder.pnlibrary.bukkit.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;

class BukkitMenusTest {
    private final Plugin owner = mock(Plugin.class);
    private final Plugin host = mock(Plugin.class);
    private final ThreadLocal<UUID> region = new ThreadLocal<>();
    private final ConcurrentLinkedQueue<QueuedAction> queued = new ConcurrentLinkedQueue<>();
    private final BukkitMenus service;

    private static final class QueuedAction {
        private final Player player;
        private final Runnable action;

        QueuedAction(Player player, Runnable action) {
            this.player = player;
            this.action = action;
        }
    }

    BukkitMenusTest() {
        when(owner.isEnabled()).thenReturn(true);
        Server server = mock(Server.class);
        when(host.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        MenuExecution execution = new MenuExecution() {
            @Override
            public void requireOwner(Player player) {
                if (!player.getUniqueId().equals(region.get())) {
                    throw new IllegalStateException("Check failed.");
                }
            }

            @Override
            public void execute(Player player, Runnable action) {
                if (player.getUniqueId().equals(region.get())) {
                    action.run();
                } else {
                    queued.add(new QueuedAction(player, action));
                }
            }
        };
        service = new BukkitMenus(host, mock(TaskScope.class), execution, (menu, holder) -> {
            Inventory inventory = mock(Inventory.class);
            when(inventory.getHolder()).thenReturn(holder);
            when(inventory.getSize()).thenReturn(menu.getSize());
            return inventory;
        });
    }

    private Menu menu(MenuRenderer renderer) {
        return new Menu("test", 1, MenuType.CHEST, Collections.emptyMap(), renderer, null, null,
            null, true);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AtomicReference<Inventory> inventory = new AtomicReference<>();
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenAnswer(call -> inventory.get());
        doAnswer(call -> {
            inventory.set(call.getArgument(0));
            return view;
        }).when(player).openInventory(any(Inventory.class));
        return player;
    }

    private <T> T on(Player player, java.util.function.Supplier<T> action) {
        region.set(player.getUniqueId());
        try {
            return action.get();
        } finally {
            region.remove();
        }
    }

    @Test
    void parallelRegionsShareIndexesAndOwnerCloseDispatchesEachNativeCleanup() throws Exception {
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            players.add(player());
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<MenuSession>> opened = new ArrayList<>();
            for (Player player : players) {
                opened.add(pool.submit(() -> on(player, () -> service.open(owner, player, menu(null)))));
            }
            for (Future<MenuSession> future : opened) {
                future.get(5, TimeUnit.SECONDS);
            }
            for (Player player : players) {
                assertNotNull(service.session(player));
            }
            service.close(owner);
            for (Player player : players) {
                assertNull(service.session(player));
                verify(player, never()).closeInventory();
            }
            assertEquals(players.size(), queued.size());
            while (!queued.isEmpty()) {
                QueuedAction next = queued.remove();
                on(next.player, () -> {
                    next.action.run();
                    return null;
                });
            }
            for (Player player : players) {
                verify(player).closeInventory();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void wrongThreadInventoryAccessFailsAndRendererFailureReleasesTheSession() {
        Player player = player();
        assertThrows(IllegalStateException.class, () -> service.open(owner, player, menu(null)));
        on(player, () -> {
            assertThrows(IllegalStateException.class, () -> service.open(owner, player, menu(session -> {
                throw new IllegalStateException("renderer");
            })));
            return null;
        });
        assertNull(service.session(player));
    }

    @Test
    void offRegionSessionCloseWaitsForItsOwningRegionAndIsIdempotent() {
        Player player = player();
        MenuSession session = on(player, () -> service.open(owner, player, menu(null)));
        assertThrows(IllegalStateException.class, () -> session.get(0));
        session.close();
        verify(player, never()).closeInventory();
        QueuedAction next = queued.remove();
        on(player, () -> {
            next.action.run();
            return null;
        });
        session.close();
        verify(player).closeInventory();
        assertNull(service.session(player));
    }
}
