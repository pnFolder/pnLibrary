package org.pnfolder.pnlibrary.api.commands.error

/**
 * The command was typed wrongly.
 *
 * @property usage generated hint, such as `/shop buy <item>`
 */
open class BadUsage(val usage: String) : CommandError() {
    override fun placeholders(): Map<String, Any?> = mapOf("usage" to usage)
}
