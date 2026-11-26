package org.pnfolder.pnlibrary.bukkit.localization;

import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.MinecraftLocale;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;

/** Loads translations that already speak Bukkit types, so callers do not wrap the neutral table themselves. */
public final class BukkitLocalization {
    private BukkitLocalization() {
    }

    /** Loads {@code locale} for {@code version} from {@code localization} and wraps it with Bukkit lookups. */
    @NotNull
    public static CompletionStage<BukkitLocaleTranslations> locale(@NotNull MinecraftLocalization localization,
                                                                   @NotNull MinecraftVersion version,
                                                                   @NotNull String locale) {
        return localization.locale(version, locale).thenApply(BukkitLocaleTranslations::wrap);
    }

    /** Loads the typed {@code locale} for {@code version} from {@code localization} and wraps it with Bukkit lookups. */
    @NotNull
    public static CompletionStage<BukkitLocaleTranslations> locale(@NotNull MinecraftLocalization localization,
                                                                   @NotNull MinecraftVersion version,
                                                                   @NotNull MinecraftLocale locale) {
        return locale(localization, version, locale.getCode());
    }
}
