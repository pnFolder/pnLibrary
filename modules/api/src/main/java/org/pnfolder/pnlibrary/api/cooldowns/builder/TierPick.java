package org.pnfolder.pnlibrary.api.cooldowns.builder;

/** Which tier of a {@code Cooldown.Builder} applies when a player holds the permissions of several. */
public enum TierPick {
    /** The first tier in the order they were declared; the default, so the order says which group matters most. */
    FIRST,

    /** The shortest wait among the tiers the player holds: every perk counts. */
    SHORTEST,

    /** The longest wait among the tiers the player holds: a penalty is never escaped by a perk. */
    LONGEST
}
