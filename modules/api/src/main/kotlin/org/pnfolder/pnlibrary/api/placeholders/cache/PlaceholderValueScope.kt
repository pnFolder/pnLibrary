package org.pnfolder.pnlibrary.api.placeholders.cache

/** Defines whether one stored placeholder value is shared or isolated by player. */
enum class PlaceholderValueScope {
    /** One value shared by the whole pnLibrary runtime. */
    GLOBAL,
    /** A separate value for every player UUID. */
    PLAYER,
}
