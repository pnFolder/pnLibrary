package org.pnfolder.pnlibrary.api.commands.error

/** The base of everything else that went wrong, and the last resort of the standard answers. */
open class CommandFailed @JvmOverloads constructor(defaultMessage: String? = null) : CommandError(defaultMessage)
