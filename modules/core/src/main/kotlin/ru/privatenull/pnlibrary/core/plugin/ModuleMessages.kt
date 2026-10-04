package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.plugin.PluginMessages

internal class ModuleMessages(
    private val owner: Any,
    private val logging: LoggingService,
) : PluginMessages {
    override fun box(title: String): MessageBox {
        require(title.isNotBlank()) { "message box title must not be blank" }
        return logging.box(owner, title.trim())
    }
}
