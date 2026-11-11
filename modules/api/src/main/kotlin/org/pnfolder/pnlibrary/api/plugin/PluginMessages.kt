package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox

interface PluginMessages {

    fun box(title: String): MessageBox
}
