package org.pnfolder.pnlibrary.api.audience

/*
 * What a platform player may offer beyond chat. A platform class implements the interfaces it supports; ask for one with
 * `player.feature<PlayerInventory>()`, which is `null` where the platform lacks it.
 */

/** Status effects and particles shown on a player. */
interface PlayerEffects {
    /** Applies a temporary status effect. */
    fun applyEffect(effect: PlayerEffect): Delivery

    /** Spawns a particle burst around the player. */
    fun spawnParticle(particle: PlayerParticle): Delivery
}

/** Runs commands as a player. */
interface PlayerCommands {
    /**
     * Runs [command] as the player, or as the console when [asConsole] is `true`.
     *
     * @param command command line without the leading slash
     */
    fun runCommand(command: String, asConsole: Boolean): Delivery
}

/** Reads what the player carries. */
interface PlayerInventory {
    /** Returns how many items matching [query] the player carries. */
    fun countItems(query: ItemQuery): Int
}

/** Reads the player's experience. */
interface PlayerExperience {
    /** Returns the experience as levels when [asLevels] is `true` and as points otherwise. */
    fun experience(asLevels: Boolean): Double
}

/** Reads where the player is. */
interface PlayerPosition {
    /** Returns the distance in blocks to the point [x], [y], [z] in [world], or `null` when the player is in another world. */
    fun distanceTo(world: String, x: Double, y: Double, z: Double): Double?
}
