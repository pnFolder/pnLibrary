package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Builds and loads a {@link TranslationRequest} from a {@link TranslationRequestDsl} block. */
public final class MinecraftLocalizationDsl {
    private MinecraftLocalizationDsl() {
    }

    /**
     * Loads translations described by {@code block}.
     *
     * <pre>{@code
     * MinecraftLocalizationDsl.load(localization, request -> {
     *     request.setVersion(MinecraftVersion.V1_21_4);
     *     request.locales(MinecraftLocale.RU_RU);
     * });
     * }</pre>
     */
    @NotNull
    public static CompletionStage<TranslationBundle> load(
            @NotNull MinecraftLocalization localization, @NotNull Consumer<TranslationRequestDsl> block) {
        TranslationRequestDsl dsl = new TranslationRequestDsl();
        block.accept(dsl);
        return localization.load(dsl.build());
    }
}
