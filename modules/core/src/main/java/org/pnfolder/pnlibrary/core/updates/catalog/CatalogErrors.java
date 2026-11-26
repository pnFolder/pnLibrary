package org.pnfolder.pnlibrary.core.updates.catalog;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;

/** The red chat line the catalog commands use for a refusal. */
public final class CatalogErrors {
    private CatalogErrors() {
    }

    public static @NotNull Component line(@NotNull String text) {
        return Component.text(text, ChatPalette.DANGER);
    }
}
