package org.pnfolder.pnlibrary.core.updates.databases

import java.nio.file.Path

/** Copies one kind of database into a file and reads such a file back. */
internal interface DatabaseEngine {
    /** [tables] limits the copy to those tables or collections; empty copies all. */
    fun dump(connection: DatabaseConnection, tables: List<String>, target: Path)

    /** Replaces what the file names; the caller has already kept the current state. */
    fun restore(connection: DatabaseConnection, source: Path)
}
