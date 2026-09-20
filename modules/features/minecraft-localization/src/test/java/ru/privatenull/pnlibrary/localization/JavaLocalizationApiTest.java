package ru.privatenull.pnlibrary.localization;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaLocalizationApiTest {
    @Test void javaBuildersAreCallable() {
        TranslationRequest request = TranslationRequest.builder()
            .version(MinecraftVersion.V1_21_4)
            .locales("ru_ru", "de_de")
            .fallback("en_us")
            .build();
        MinecraftLocalization service = MinecraftLocalization.builder()
            .cacheDirectory(Paths.get("build", "java-api-cache"))
            .memoryEntries(4)
            .downloadConcurrency(1)
            .build();
        assertEquals(MinecraftVersion.V1_21_4, request.getVersion());
        service.close();
    }
}
