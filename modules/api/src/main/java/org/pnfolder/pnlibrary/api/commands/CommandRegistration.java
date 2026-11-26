package org.pnfolder.pnlibrary.api.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Closeable handle for one live native command registration. */
public interface CommandRegistration extends AutoCloseable {
    /** Portable definition represented by this registration. */
    @NotNull
    CommandDefinition getCommand();

    /** Whether this registration has already been closed. */
    boolean isClosed();

    /** Removes the native command and releases this registration. */
    @Override
    void close();
}
