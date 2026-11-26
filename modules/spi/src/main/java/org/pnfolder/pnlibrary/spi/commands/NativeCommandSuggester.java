package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Computes completions for a native source of type {@code S}. */
@FunctionalInterface
public interface NativeCommandSuggester<S> {
    /** Suggests completions for {@code source}, invoked as {@code alias} with {@code arguments}. */
    @NotNull
    CompletionStage<List<String>> suggest(@NotNull S source, @NotNull String alias, @NotNull String[] arguments);
}
