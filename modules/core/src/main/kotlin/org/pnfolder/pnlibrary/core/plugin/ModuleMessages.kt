package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.plugin.PluginMessages

/** Console message boxes on behalf of one module. */
internal class ModuleMessages(
    private val owner: Any,
    private val logging: LoggingService,
) : PluginMessages {
    override fun box(title: String): MessageBox {
        require(title.isNotBlank()) { "Message box title must not be blank" }
        return logging.box(owner, title.trim())
    }
}
