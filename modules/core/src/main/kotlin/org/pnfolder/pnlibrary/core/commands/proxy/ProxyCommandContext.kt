package org.pnfolder.pnlibrary.core.commands.proxy

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender

/** Builds the context of a command typed on a proxy; replies to the proxy control command name it with [ProxyCommandNames]. */
fun proxyCommandContext(audience: Audience, alias: String, arguments: Array<String>): CommandContext =
    CommandContext(
        sender = ProxyCommandSender(audience, ProxyCommandNames.owns(alias)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

/** Delegates portable command replies to the native audience bridge, rewriting `/pn` for the proxy control command. */
private class ProxyCommandSender(
    private val audience: Audience,
    private val proxyControl: Boolean,
) : CommandSender, ForwardingAudience.Single {
    override fun audience(): Audience = audience

    override fun send(message: Component) = audience.sendMessage(shown(message))

    override fun sendMessage(message: Component) = audience.sendMessage(shown(message))

    override fun sendActionBar(message: Component) = audience.sendActionBar(message)

    private fun shown(message: Component) = if (proxyControl) ProxyCommandNames.rewrite(message) else message
}
