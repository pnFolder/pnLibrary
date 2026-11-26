package org.pnfolder.pnlibrary.updates.databases;

import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Copies one kind of database into a file and reads such a file back. */
public interface DatabaseEngine {
    /** {@code tables} limits the copy to those tables or collections; empty copies all. */
    void dump(@NotNull DatabaseConnection connection, @NotNull List<String> tables, @NotNull Path target) throws Exception;

    /** Replaces what the file names; the caller has already kept the current state. */
    void restore(@NotNull DatabaseConnection connection, @NotNull Path source) throws Exception;
}
