package org.pnfolder.pnlibrary.api.events.player;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

/** A player left: on Bukkit after the quit event, on a proxy after disconnecting. */
public final class PlayerLeftEvent extends PlayerLifecycleEvent {
    /** Creates the notification; see {@link PlayerLifecycleEvent}. */
    public PlayerLeftEvent(@NotNull UUID uniqueId, @NotNull String name, @Nullable OnlinePlayer player) {
        super(uniqueId, name, player);
    }
}
