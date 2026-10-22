package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.plugin.LifecycleRow

/**
 * A lifecycle card that lets the plugin hide the rows pnLibrary adds by itself before any of its own rows are added.
 *
 * The automatic rows are known up front; the plugin's rows are recorded and replayed after them, so hiding never
 * touches a plugin's own row. [hide] may be called in the fluent chain and reads as what it does.
 */
internal class HidingLifecycleBox(
    private val delegate: MessageBox,
    private val autoRows: List<AutoRow>,
) : MessageBox {
    /** One automatic row: its [tag], whether it is a "not configured" row, and how it is written. */
    class AutoRow(val tag: LifecycleRow, val notConfigured: Boolean, val write: (MessageBox) -> Unit)

    private val hidden = linkedSetOf<LifecycleRow>()
    private val pluginRows = mutableListOf<(MessageBox) -> Unit>()

    override fun ok(label: String, detail: String) = apply { pluginRows += { it.ok(label, detail) } }
    override fun warn(label: String, detail: String) = apply { pluginRows += { it.warn(label, detail) } }
    override fun skip(label: String, detail: String) = apply { pluginRows += { it.skip(label, detail) } }
    override fun fail(label: String, detail: String, error: Throwable?) =
        apply { pluginRows += { it.fail(label, detail, error) } }

    override fun hide(vararg rows: LifecycleRow) = apply { hidden += rows }

    override fun show() {
        autoRows.forEach { row ->
            if (row.tag in hidden) return@forEach
            if (LifecycleRow.NOT_CONFIGURED in hidden && row.notConfigured) return@forEach
            row.write(delegate)
        }
        pluginRows.forEach { it(delegate) }
        delegate.show()
    }
}
