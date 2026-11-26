package org.pnfolder.pnlibrary.bukkit.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.localization.Translation;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

class ForwardLookupTest {
    private final BukkitLocaleTranslations translations = BukkitLocaleTranslations.wrap(
        new FakeLocaleTranslations(modernValues())
    );

    // A pre-1.13 table carries the old key scheme; the same calls must still resolve.
    private final BukkitLocaleTranslations legacyTranslations = BukkitLocaleTranslations.wrap(
        new FakeLocaleTranslations(legacyValues())
    );

    private static Map<String, String> modernValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("block.minecraft.stone", "Камень");
        values.put("item.minecraft.diamond", "Алмаз");
        values.put("enchantment.minecraft.sharpness", "Острота");
        values.put("entity.minecraft.zombie", "Зомби");
        values.put("effect.minecraft.haste", "Спешка");
        values.put("biome.minecraft.plains", "Равнина");
        values.put("color.minecraft.red", "Красный");
        values.put("gameMode.survival", "Выживание");
        return values;
    }

    private static Map<String, String> legacyValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enchantment.damage.all", "Острота");
        values.put("effect.digSpeed", "Спешка");
        values.put("entity.Zombie.name", "Зомби");
        return values;
    }

    @Test
    void translateResolvesAMaterialDirectly() {
        Translation stone = translations.translate(Material.STONE);
        assertEquals("Камень", stone.getText());
        assertTrue(stone.isLocalized());
        assertEquals("Алмаз", translations.translate(Material.DIAMOND).getText());
    }

    @Test
    void translateFallsBackToAReadableFormWhenAbsent() {
        Translation bedrock = translations.translate(Material.BEDROCK);
        assertEquals("bedrock", bedrock.getText());
        assertFalse(bedrock.isLocalized());
    }

    @Test
    @SuppressWarnings("deprecation")
    void translateResolvesAnEnchantmentByModernKey() {
        Translation sharpness = translations.translate(Enchantment.DAMAGE_ALL);
        assertEquals("Острота", sharpness.getText());
        assertTrue(sharpness.isLocalized());
    }

    @Test
    void translateResolvesAnEntityType() {
        Translation zombie = translations.translate(EntityType.ZOMBIE);
        assertEquals("Зомби", zombie.getText());
        assertTrue(zombie.isLocalized());
    }

    @Test
    void translateResolvesAnEffectByModernKeyDespiteTheLegacyBukkitName() {
        Translation haste = translations.translate(PotionEffectType.FAST_DIGGING);
        assertEquals("Спешка", haste.getText());
        assertTrue(haste.isLocalized());
    }

    @Test
    void translateResolvesABiome() {
        Translation plains = translations.translate(Biome.PLAINS);
        assertEquals("Равнина", plains.getText());
        assertTrue(plains.isLocalized());
    }

    @Test
    void translateResolvesADyeColor() {
        Translation red = translations.translate(DyeColor.RED);
        assertEquals("Красный", red.getText());
        assertTrue(red.isLocalized());
        assertEquals(DyeColor.RED, translations.colors().findExact("Красный").get(0).getValue());
        assertEquals(1, translations.colors().findExact("Красный").size());
    }

    @Test
    void translateResolvesAGameMode() {
        Translation survival = translations.translate(GameMode.SURVIVAL);
        assertEquals("Выживание", survival.getText());
        assertTrue(survival.isLocalized());
        assertEquals(GameMode.SURVIVAL, translations.gameModes().findExact("Выживание").get(0).getValue());
        assertEquals(1, translations.gameModes().findExact("Выживание").size());
    }

    @Test
    @SuppressWarnings("deprecation")
    void translateResolvesAgainstAPre113TableViaLegacyKeys() {
        assertEquals("Острота", legacyTranslations.translate(Enchantment.DAMAGE_ALL).getText());
        assertEquals("Спешка", legacyTranslations.translate(PotionEffectType.FAST_DIGGING).getText());
        assertEquals("Зомби", legacyTranslations.translate(EntityType.ZOMBIE).getText());
        assertTrue(legacyTranslations.translate(EntityType.ZOMBIE).isLocalized());
    }

    @Test
    void reverseSearchesReadQueriesAgainstTheLayoutsOfTheTable() throws Exception {
        KeyboardLayouts registry = KeyboardLayouts.create();
        KeyboardLayout dvorak = KeyboardLayout.of("bukkit-test-dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[");
        BukkitLocaleTranslations table = BukkitLocaleTranslations.wrap(
            new FakeLocaleTranslations(Collections.singletonMap("block.minecraft.stone", "Stone"), registry)
        );
        String typed = KeyboardLayout.convert("stone", dvorak, KeyboardLayout.EN_US);

        assertNull(table.materials().best(typed));
        AutoCloseable handle = registry.register(dvorak);
        assertEquals(Material.STONE, table.materials().best(typed).getValue());
        handle.close();
        assertNull(table.materials().best(typed));
    }
}
