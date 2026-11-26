package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.GameMode;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation key of a {@link GameMode}. */
public final class GameModeTranslatable implements TranslatableType<GameMode> {
    public static final GameModeTranslatable INSTANCE = new GameModeTranslatable();

    private GameModeTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(GameMode value) {
        return Collections.singletonList(TranslationCategory.GAME_MODE.key(value.name().toLowerCase(Locale.ROOT)));
    }

    @NotNull
    @Override
    public String fallbackName(GameMode value) {
        return TranslationNames.readable(value.name());
    }
}
