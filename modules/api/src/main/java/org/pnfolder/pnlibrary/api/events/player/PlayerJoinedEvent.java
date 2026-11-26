package org.pnfolder.pnlibrary.api.events.player;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

/** A player finished joining: on Bukkit after the join event, on a proxy after login. */
public final class PlayerJoinedEvent extends PlayerLifecycleEvent {
    /** Creates the notification; see {@link PlayerLifecycleEvent}. */
    public PlayerJoinedEvent(@NotNull UUID uniqueId, @NotNull String name, @Nullable OnlinePlayer player) {
        super(uniqueId, name, player);
    }
}
