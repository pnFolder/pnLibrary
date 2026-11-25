package org.pnfolder.pnlibrary.api.commands.error

/**
 * An argument that does not parse.
 *
 * @property argument name of the argument
 * @property value what was typed
 */
open class InvalidArgument(usage: String, val argument: String, val value: String) : BadUsage(usage) {
    override fun placeholders(): Map<String, Any?> = super.placeholders() + mapOf("argument" to argument, "value" to value)
}
