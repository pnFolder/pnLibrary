package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.logging.MessageBox

/** Creates buffered, plugin-bound console messages for arbitrary operations. */
interface PluginMessages {
    /** Creates a neutral message box. Nothing is printed until [MessageBox.show]. */
    fun box(title: String): MessageBox
}
