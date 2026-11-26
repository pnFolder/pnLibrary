package org.pnfolder.pnlibrary.bukkit.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.MinecraftLocale;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

class BukkitLocalizationTest {
    private final Map<String, String> values = Collections.singletonMap("item.minecraft.diamond", "Алмаз");
    private final LocaleTranslations table = new FakeLocaleTranslations(values);
    private final List<String> loaded = new ArrayList<>();

    private final MinecraftLocalization localization = new MinecraftLocalization() {
        @NotNull
        @Override
        public KeyboardLayouts getLayouts() {
            return KeyboardLayouts.builtIn;
        }

        @Override
        public CompletionStage<List<MinecraftVersion>> availableVersions() {
            throw new IllegalStateException("not needed");
        }

        @Override
        public CompletionStage<List<String>> availableLocales(@NotNull MinecraftVersion version) {
            throw new IllegalStateException("not needed");
        }

        @Override
        public CompletionStage<TranslationBundle> load(@NotNull TranslationRequest request) {
            throw new IllegalStateException("not needed");
        }

        @Override
        public CompletionStage<TranslationBundle> refresh(@NotNull TranslationRequest request) {
            throw new IllegalStateException("not needed");
        }

        @Override
        public CompletionStage<LocaleTranslations> locale(@NotNull MinecraftVersion version, @NotNull String locale) {
            loaded.add(locale);
            return CompletableFuture.completedFuture(table);
        }

        @Override
        public void close() {
        }
    };

    @Test
    void aLoadedTableAlreadyTranslatesBukkitValues() {
        BukkitLocaleTranslations ru = BukkitLocalization.locale(localization, MinecraftVersion.V1_20_5, MinecraftLocale.RU_RU)
            .toCompletableFuture().join();

        assertEquals("Алмаз", ru.translate(Material.DIAMOND).getText());
        assertEquals(Collections.singletonList("ru_ru"), loaded);
    }

    @Test
    void aLocaleCodeStringLoadsTheSameTable() {
        BukkitLocaleTranslations ru = BukkitLocalization.locale(localization, MinecraftVersion.V1_20_5, "ru_ru")
            .toCompletableFuture().join();

        assertEquals("Алмаз", ru.translate(Material.DIAMOND).getText());
    }
}
