package org.pnfolder.pnlibrary.core.commands.proxy

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender

/** Builds the context of a command typed on a proxy; replies to the proxy control command name it with [ProxyCommandNames]. */
fun proxyCommandContext(audience: AudienceSender, alias: String, arguments: Array<String>): CommandContext =
    CommandContext(
        sender = ProxyCommandSender(audience, ProxyCommandNames.owns(alias)),
        arguments = arguments.toList(),
        invokedAlias = alias,
        currentInput = arguments.lastOrNull().orEmpty(),
    )

/** Delegates portable command replies to the native audience bridge, rewriting `/pn` for the proxy control command. */
private class ProxyCommandSender(
    private val audience: AudienceSender,
    private val proxyControl: Boolean,
) : CommandSender, AudienceSender by audience {
    override fun send(message: Component) = audience.sendMessage(shown(message))

    // Explicit overrides preserve native delivery instead of CommandSender's fallback defaults.
    override fun sendMessage(text: Component) = audience.sendMessage(shown(text))

    override fun actionBar(text: Component) = audience.actionBar(text)

    override fun playSound(sound: Sound): Delivery = audience.playSound(sound)

    private fun shown(message: Component) = if (proxyControl) ProxyCommandNames.rewrite(message) else message
}
