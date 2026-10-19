package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.pnfolder.pnlibrary.core.ui.ChatPalette
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands.SettingSaved

/**
 * Titles in the chat palette for the Bukkit title API, which takes legacy strings on every version since 1.8;
 * Adventure downsamples each color to its legacy equivalent.
 *
 * @property title the large line
 * @property subtitle the line under it
 */
internal data class BukkitTitle(val title: String, val subtitle: String) {
    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()

        /** A saved setting: a check and its name, then what changed, such as `stable → beta`. */
        fun settingSaved(saved: SettingSaved): BukkitTitle {
            val subtitle = if (saved.from == saved.to) {
                text(saved.to, ChatPalette.LABEL)
            } else {
                text(saved.from, ChatPalette.LABEL).append(text(" → ", ChatPalette.ACCENT)).append(text(saved.to, ChatPalette.TEXT))
            }
            return of(text("✔ ", ChatPalette.ACCENT).append(text(saved.setting, ChatPalette.TEXT)), subtitle)
        }

        /** Shown to an administrator on joining while [pending] updates are available or prepared. */
        fun updatesPending(pending: Int): BukkitTitle = of(
            text("Обновления pnFolder", ChatPalette.ACCENT),
            text("Доступно: ", ChatPalette.TEXT).append(text("$pending", ChatPalette.LABEL)),
        )

        private fun of(title: Component, subtitle: Component) = BukkitTitle(LEGACY.serialize(title), LEGACY.serialize(subtitle))

        private fun text(value: String, color: TextColor): Component = Component.text(value, color)
    }
}
