package org.pnfolder.pnlibrary.api.logging

import org.pnfolder.pnlibrary.api.plugin.lifecycle.LifecycleRow

/**
 * Fluent startup, shutdown, or operation summary displayed as one console block.
 *
 * Rows may be added until [show] is called. A box can be displayed exactly once, and
 * modifying or displaying it again fails with [IllegalStateException]. Failure rows
 * preserve their exception in the diagnostic log buffer.
 */
interface MessageBox {

    /** Adds a successful row. */
    fun ok(label: String, detail: String): MessageBox

    /** Adds a row for something that works but needs attention. */
    fun warn(label: String, detail: String): MessageBox

    /** Adds a row for a step that was skipped on purpose. */
    fun skip(label: String, detail: String): MessageBox

    /** Adds a failed row; [error], when given, is kept in the diagnostic log buffer. */
    fun fail(label: String, detail: String, error: Throwable? = null): MessageBox

    /**
     * Hides the rows pnLibrary adds by itself to a lifecycle card (enabled or disabled), for example
     * `lifecycle.enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.NOT_CONFIGURED)`. The plugin's own rows are
     * never hidden, and on a generic box this does nothing.
     */
    fun hide(vararg rows: LifecycleRow): MessageBox = this

    /** Writes the complete box to the owning plugin's native console. */
    fun show()
}
