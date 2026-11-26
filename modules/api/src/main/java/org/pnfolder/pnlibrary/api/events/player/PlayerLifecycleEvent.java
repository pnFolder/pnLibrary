package org.pnfolder.pnlibrary.api.events.player;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.events.Event;

/**
 * Platform-independent notification about a player entering or leaving the server or proxy.
 *
 * <p>pnLibrary publishes these through the event service on Bukkit, BungeeCord and Velocity, so one listener works
 * everywhere. They are announcements: a listener cannot veto the join or the quit.
 */
public abstract class PlayerLifecycleEvent extends Event {
    private final UUID uniqueId;
    private final String name;
    private final OnlinePlayer player;

    /**
     * Creates the notification.
     *
     * @param uniqueId the player's account ID
     * @param name the player's name at that moment
     * @param player the player as a library audience, or {@code null} when the platform no longer knows the player
     */
    protected PlayerLifecycleEvent(@NotNull UUID uniqueId, @NotNull String name, @Nullable OnlinePlayer player) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.player = player;
    }

    /** The player's account ID. */
    @NotNull
    public final UUID getUniqueId() {
        return uniqueId;
    }

    /** The player's name at that moment. */
    @NotNull
    public final String getName() {
        return name;
    }

    /** The player as a library audience, or {@code null} when the platform no longer knows the player. */
    @Nullable
    public final OnlinePlayer getPlayer() {
        return player;
    }
}
