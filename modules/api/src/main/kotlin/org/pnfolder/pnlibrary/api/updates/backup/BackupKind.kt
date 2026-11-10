package org.pnfolder.pnlibrary.api.updates.backup

/** What a backed-up file holds, so the menu and commands can tell an administrator what a copy contains. */
enum class BackupKind {
    /** Settings and messages; small files. */
    CONFIGURATION,

    /** A database kept in a file, such as SQLite or H2. */
    DATABASE,

    /** Data about players: balances, profiles, progress. */
    PLAYER_DATA,

    /** Anything else a plugin wants to be able to roll back. */
    OTHER,
}

/** The kind and the display name of one backed-up file. A blank [name] means the library shows the kind. */
data class BackupLabel(val kind: BackupKind, val name: String = "") {
    init {
        require(name.length <= 64 && name.none(Char::isISOControl)) { "Backup name must be at most 64 characters without control characters" }
    }
}
