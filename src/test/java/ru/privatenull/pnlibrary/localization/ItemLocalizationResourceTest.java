package ru.privatenull.pnlibrary.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLocalizationResourceTest {
    @Test
    void bundlesRussianAndEnglishMinecraftTranslations() {
        ItemLocalization russian = ItemLocalization.load(MinecraftLocale.RU_RU);
        ItemLocalization english = ItemLocalization.load(MinecraftLocale.EN_US);

        assertTrue(russian.translations().size() > 5_000);
        assertTrue(english.translations().size() > 5_000);
        assertEquals("Камень", russian.translate("block.minecraft.stone"));
        assertEquals("Stone", english.translate("block.minecraft.stone"));
    }

    @Test
    void acceptsShortAndFullLocaleNames() {
        assertEquals(MinecraftLocale.RU_RU, MinecraftLocale.parse("ru"));
        assertEquals(MinecraftLocale.EN_US, MinecraftLocale.parse("en_US"));
    }
}
