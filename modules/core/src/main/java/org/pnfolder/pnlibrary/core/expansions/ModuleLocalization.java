package org.pnfolder.pnlibrary.core.expansions;

import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

/** One module's view of the shared translation loader: loading is shared, closing is not, and its layouts end with the module. */
public final class ModuleLocalization implements MinecraftLocalization {
    private final MinecraftLocalization shared;
    private final KeyboardLayouts moduleLayouts;

    public ModuleLocalization(@NotNull MinecraftLocalization shared, @NotNull ModuleBinding module) {
        this.shared = shared;
        this.moduleLayouts = new ModuleKeyboardLayouts(shared.getLayouts(), module);
    }

    @Override
    public @NotNull KeyboardLayouts getLayouts() {
        return moduleLayouts;
    }

    @Override
    public @NotNull CompletionStage<List<MinecraftVersion>> availableVersions() {
        return shared.availableVersions();
    }

    @Override
    public @NotNull CompletionStage<List<String>> availableLocales(@NotNull MinecraftVersion version) {
        return shared.availableLocales(version);
    }

    @Override
    public @NotNull CompletionStage<TranslationBundle> load(@NotNull TranslationRequest request) {
        return shared.load(request);
    }

    @Override
    public @NotNull CompletionStage<TranslationBundle> refresh(@NotNull TranslationRequest request) {
        return shared.refresh(request);
    }

    @Override
    public void close() {
    }
}
