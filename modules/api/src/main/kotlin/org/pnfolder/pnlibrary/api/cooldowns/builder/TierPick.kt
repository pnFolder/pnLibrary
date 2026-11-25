package org.pnfolder.pnlibrary.api.cooldowns.builder

import org.pnfolder.pnlibrary.api.cooldowns.Cooldown

/** Which tier of a [Cooldown.Builder] applies when a player holds the permissions of several. */
enum class TierPick {
    /** The first tier in the order they were declared; the default, so the order says which group matters most. */
    FIRST,

    /** The shortest wait among the tiers the player holds: every perk counts. */
    SHORTEST,

    /** The longest wait among the tiers the player holds: a penalty is never escaped by a perk. */
    LONGEST,
}
