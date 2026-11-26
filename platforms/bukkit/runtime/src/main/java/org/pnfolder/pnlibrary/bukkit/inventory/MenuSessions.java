package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

// Folia opens menus in parallel across regions, so all state is guarded by a lock.
final class MenuSessions {
    private final Object lock = new Object();
    private final Map<UUID, BukkitMenuSession> byPlayer = new HashMap<>();
    private final Map<Plugin, Set<BukkitMenuSession>> byOwner = new IdentityHashMap<>();
    private volatile boolean closed;

    boolean isClosed() {
        return closed;
    }

    BukkitMenuSession of(Player player) {
        synchronized (lock) {
            return byPlayer.get(player.getUniqueId());
        }
    }

    List<BukkitMenuSession> of(Plugin owner) {
        synchronized (lock) {
            Set<BukkitMenuSession> owned = byOwner.get(owner);
            return owned == null ? new ArrayList<>() : new ArrayList<>(owned);
        }
    }

    void add(BukkitMenuSession session) {
        synchronized (lock) {
            if (closed || !session.getOwner().isEnabled()) {
                throw new IllegalStateException("Menu service or owner is disabled");
            }
            byPlayer.put(session.getPlayer().getUniqueId(), session);
            Set<BukkitMenuSession> owned = byOwner.get(session.getOwner());
            if (owned == null) {
                owned = new LinkedHashSet<>();
                byOwner.put(session.getOwner(), owned);
            }
            owned.add(session);
        }
    }

    void remove(BukkitMenuSession session) {
        synchronized (lock) {
            UUID id = session.getPlayer().getUniqueId();
            if (byPlayer.get(id) == session) {
                byPlayer.remove(id);
            }
            Set<BukkitMenuSession> owned = byOwner.get(session.getOwner());
            if (owned == null) {
                return;
            }
            owned.remove(session);
            if (owned.isEmpty()) {
                byOwner.remove(session.getOwner());
            }
        }
    }

    /** Marks the service closed and returns the sessions that were open, or null when it was already closed. */
    List<BukkitMenuSession> shutdown() {
        synchronized (lock) {
            if (closed) {
                return null;
            }
            closed = true;
            return new ArrayList<>(byPlayer.values());
        }
    }
}
