package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.event.ClickEvent
import java.util.regex.Pattern

/**
 * The names of the control command on a proxy.
 *
 * A player's `/pn` on a proxied network reaches the proxy first, so a proxy that owned `/pn` would hide the one on the
 * game server. The proxy therefore answers to `/pnproxy` and `/pnp`, and [rewrite] turns the `/pn` that the shared
 * command text and buttons mention into [NAME], so a click on the proxy runs the proxy command.
 */
object ProxyCommandNames {
    /** Name of the proxy control command. */
    const val NAME = "pnproxy"

    /** Shorter names of the proxy control command. */
    val ALIASES = setOf("pnp")

    private val ROOT = Pattern.compile("/pn(?=[ \t]|$)")
    private val TEXT = TextReplacementConfig.builder().match(ROOT).replacement("/$NAME").build()

    /** Whether [alias] is one of the proxy control command's names. */
    fun owns(alias: String): Boolean = alias.equals(NAME, ignoreCase = true) || ALIASES.any { it.equals(alias, ignoreCase = true) }

    /** [message] with `/pn` in its text and in its click commands replaced by `/pnproxy`. */
    fun rewrite(message: Component): Component = clicks(message.replaceText(TEXT))

    private fun clicks(component: Component): Component {
        val click = component.clickEvent()
        val result = component.children(component.children().map(::clicks))
        val runs = click != null && (click.action() == ClickEvent.Action.RUN_COMMAND || click.action() == ClickEvent.Action.SUGGEST_COMMAND)
        if (click == null || !runs || !click.value().startsWith("/pn")) return result
        return result.clickEvent(ClickEvent.clickEvent(click.action(), ROOT.matcher(click.value()).replaceFirst("/$NAME")))
    }
}
