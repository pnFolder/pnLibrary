package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox

/** Console message boxes bound to one plugin. */
interface PluginMessages {

    /** Starts a console block titled [title]; nothing is printed until `show()`. */
    fun box(title: String): MessageBox
}
