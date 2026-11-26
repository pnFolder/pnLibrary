package org.pnfolder.pnlibrary.core.expansions;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationException;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

/**
 * One translation loader shared by every module, which reach it only through {@link #bind}. Nothing is downloaded when it
 * starts: modules request the versions and locales they need and the loader caches them by version and locale.
 * Keyboard layouts outlive disable/enable cycles and are dropped when the expansion closes.
 */
public final class LocalizationExpansion extends AbstractExpansion implements ModuleBoundExpansion<MinecraftLocalization> {
    /** The default of 8 bundles is sized for a single plugin; this cache serves all of them across many locales. */
    private static final int MEMORY_ENTRIES = 32;

    private final Path cacheDirectory;
    private final KeyboardLayouts layoutRegistry = KeyboardLayouts.create();
    private volatile MinecraftLocalization active;
    private final MinecraftLocalization shared = new Shared();

    public LocalizationExpansion(@NotNull Path cacheDirectory) {
        super("localization");
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public @NotNull MinecraftLocalization bind(@NotNull ModuleBinding module) {
        return new ModuleLocalization(shared, module);
    }

    @Override
    protected void onEnable() {
        active = MinecraftLocalization.builder()
                .cacheDirectory(cacheDirectory)
                .memoryEntries(MEMORY_ENTRIES)
                .layouts(layoutRegistry)
                .build();
    }

    @Override
    protected void onDisable() {
        MinecraftLocalization current = active;
        if (current != null) {
            current.close();
        }
        active = null;
    }

    @Override
    protected void onClose() {
        layoutRegistry.close();
    }

    private MinecraftLocalization current() {
        MinecraftLocalization current = active;
        if (current == null) {
            throw new TranslationException(TranslationException.Reason.CLOSED, "Minecraft localization is disabled");
        }
        return current;
    }

    /** Follows enable/disable cycles of the loader; closing it does nothing. */
    private final class Shared implements MinecraftLocalization {
        @Override
        public @NotNull KeyboardLayouts getLayouts() {
            return layoutRegistry;
        }

        @Override
        public @NotNull CompletionStage<List<MinecraftVersion>> availableVersions() {
            return current().availableVersions();
        }

        @Override
        public @NotNull CompletionStage<List<String>> availableLocales(@NotNull MinecraftVersion version) {
            return current().availableLocales(version);
        }

        @Override
        public @NotNull CompletionStage<TranslationBundle> load(@NotNull TranslationRequest request) {
            return current().load(request);
        }

        @Override
        public @NotNull CompletionStage<TranslationBundle> refresh(@NotNull TranslationRequest request) {
            return current().refresh(request);
        }

        @Override
        public void close() {
        }
    }
}
