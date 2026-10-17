package ru.privatenull.pnlibrary.bukkit.commands

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent

/** Builds the clickable chat buttons shared by the `/pn` control command. */
@Suppress("DEPRECATION")
internal object BukkitChatButtons {
    fun runCommand(text: String, color: ChatColor, command: String, hint: String): TextComponent =
        button(text, color, ClickEvent(ClickEvent.Action.RUN_COMMAND, command), hint)

    fun openUrl(text: String, color: ChatColor, url: String, hint: String): TextComponent =
        button(text, color, ClickEvent(ClickEvent.Action.OPEN_URL, url), hint)

    private fun button(text: String, color: ChatColor, action: ClickEvent, hint: String): TextComponent =
        TextComponent(text).apply {
            this.color = color
            isBold = true
            clickEvent = action
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder(hint).color(ChatColor.GRAY).create())
        }
}
