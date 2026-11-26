package org.pnfolder.pnlibrary.api.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.LifecycleRow;

/**
 * Fluent startup, shutdown, or operation summary displayed as one console block.
 *
 * <p>Rows may be added until {@link #show()} is called. A box can be displayed exactly once, and
 * modifying or displaying it again fails with {@link IllegalStateException}. Failure rows
 * preserve their exception in the diagnostic log buffer.
 */
public interface MessageBox {
    /** Adds a successful row. */
    @NotNull MessageBox ok(@NotNull String label, @NotNull String detail);

    /** Adds a row for something that works but needs attention. */
    @NotNull MessageBox warn(@NotNull String label, @NotNull String detail);

    /** Adds a row for a step that was skipped on purpose. */
    @NotNull MessageBox skip(@NotNull String label, @NotNull String detail);

    /** Adds a failed row; {@code error}, when given, is kept in the diagnostic log buffer. */
    @NotNull MessageBox fail(@NotNull String label, @NotNull String detail, @Nullable Throwable error);

    /** Adds a failed row without an exception. */
    default @NotNull MessageBox fail(@NotNull String label, @NotNull String detail) {
        return fail(label, detail, null);
    }

    /**
     * Hides the rows pnLibrary adds by itself to a lifecycle card (enabled or disabled), for example
     * {@code lifecycle.enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.NOT_CONFIGURED)}. The plugin's own rows
     * are never hidden, and on a generic box this does nothing.
     */
    default @NotNull MessageBox hide(@NotNull LifecycleRow... rows) {
        return this;
    }

    /** Writes the complete box to the owning plugin's native console. */
    void show();
}
