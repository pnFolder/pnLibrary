package org.pnfolder.pnlibrary.api.commands.error

/**
 * The sender may not use the command or subcommand.
 *
 * @property permission missing permission; empty when an availability rule hid the command instead
 */
open class NoPermission(val permission: String) : CommandError() {
    override fun placeholders() = mapOf("permission" to permission)
}
