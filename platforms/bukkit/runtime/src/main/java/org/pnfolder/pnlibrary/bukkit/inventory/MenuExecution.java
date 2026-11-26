package org.pnfolder.pnlibrary.bukkit.inventory;

import org.bukkit.entity.Player;

/** Native inventory access must run on the player's owning thread, including bulk cleanup. */
interface MenuExecution {
    /** Fails when the current thread may not touch {@code player}'s inventory. */
    void requireOwner(Player player);

    /** Runs {@code action} on the thread that owns {@code player}. */
    void execute(Player player, Runnable action);
}
