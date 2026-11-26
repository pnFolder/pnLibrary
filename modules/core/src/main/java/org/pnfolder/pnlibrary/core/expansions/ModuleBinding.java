package org.pnfolder.pnlibrary.core.expansions;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/** What a view owns on behalf of its module: closed, newest first, when the module closes. */
public interface ModuleBinding {
    /** Acquires a resource for the module. Throws {@link IllegalStateException} without acquiring when the module is closed. */
    @NotNull <R extends AutoCloseable> R own(@NotNull Supplier<R> acquire);
}
