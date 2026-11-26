package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;

/** Runs a command for a native source of type {@code S}. */
@FunctionalInterface
public interface NativeCommandExecutor<S> {
    /** Runs the command for {@code source}, invoked as {@code alias} with {@code arguments}. */
    void execute(@NotNull S source, @NotNull String alias, @NotNull String[] arguments);
}
