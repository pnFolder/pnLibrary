package org.pnfolder.pnlibrary.api.commands.error

/**
 * A word the command does not know, or one too many.
 *
 * @property value the unexpected word
 */
open class UnknownArgument(usage: String, val value: String) : BadUsage(usage) {
    override fun placeholders(): Map<String, Any?> = super.placeholders() + ("value" to value)
}
