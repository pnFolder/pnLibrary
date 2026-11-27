package org.pnfolder.pnlibrary.bukkit.audience;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogCommands;

/**
 * Titles in the chat palette for the Bukkit title API, which takes legacy strings on every version since 1.8;
 * Adventure downsamples each color to its legacy equivalent.
 */
public final class BukkitTitle {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final String title;
    private final String subtitle;

    public BukkitTitle(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BukkitTitle && title.equals(((BukkitTitle) other).title)
            && subtitle.equals(((BukkitTitle) other).subtitle);
    }

    @Override
    public int hashCode() {
        return 31 * title.hashCode() + subtitle.hashCode();
    }

    @Override
    public String toString() {
        return "BukkitTitle(title=" + title + ", subtitle=" + subtitle + ")";
    }

    /** A saved setting: a check and its name, then what changed, such as {@code stable → beta}. */
    public static BukkitTitle settingSaved(UpdateCatalogCommands.SettingSaved saved) {
        Component subtitle;
        if (saved.getFrom().equals(saved.getTo())) {
            subtitle = text(saved.getTo(), ChatPalette.LABEL);
        } else {
            subtitle = text(saved.getFrom(), ChatPalette.LABEL).append(text(" → ", ChatPalette.ACCENT))
                .append(text(saved.getTo(), ChatPalette.TEXT));
        }
        return of(text("✔ ", ChatPalette.ACCENT).append(text(saved.getSetting(), ChatPalette.TEXT)), subtitle);
    }

    /** Shown to an administrator on joining while {@code pending} updates are available or prepared. */
    public static BukkitTitle updatesPending(int pending) {
        return of(text("Обновления pnFolder", ChatPalette.ACCENT),
            text("Доступно: ", ChatPalette.TEXT).append(text(String.valueOf(pending), ChatPalette.LABEL)));
    }

    private static BukkitTitle of(Component title, Component subtitle) {
        return new BukkitTitle(LEGACY.serialize(title), LEGACY.serialize(subtitle));
    }

    private static Component text(String value, TextColor color) {
        return Component.text(value, color);
    }
}
