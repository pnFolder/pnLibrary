package org.pnfolder.pnlibrary.api.placeholders.argument

/** Thrown by [ArgumentInput.fail]; it is the answer "this text does not fit", not an error of the plugin. */
class ArgumentRejected(val reason: String) : RuntimeException(reason, null, false, false)
