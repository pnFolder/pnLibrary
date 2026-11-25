package org.pnfolder.pnlibrary.api.commands.error

/** A player argument that names nobody online. */
open class PlayerNotFound(usage: String, argument: String, value: String) : InvalidArgument(usage, argument, value)
