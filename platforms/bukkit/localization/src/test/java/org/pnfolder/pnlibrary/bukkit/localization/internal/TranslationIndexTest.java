package org.pnfolder.pnlibrary.bukkit.localization.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.bukkit.localization.BukkitLocaleTranslations;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMatch;

class TranslationIndexTest {
    @Test
    void searchNormalizesRussianSpellingAndRanksExactBeforePrefix() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("item.minecraft.stone", "Камень");
        values.put("item.minecraft.stone_sword", "Каменный меч");
        values.put("item.minecraft.diamond_sword", "Алмазный меч");
        TranslationIndex<Material> index = BukkitLocaleTranslations.wrap(new FakeLocaleTranslations(values)).materials();

        assertEquals(Material.STONE, index.findExact("  КАМЕНЬ ").get(0).getValue());
        assertEquals(1, index.findExact("  КАМЕНЬ ").size());
        List<String> keys = index.search("камен").stream().map(TranslationMatch::getKey).collect(Collectors.toList());
        assertEquals(Arrays.asList("item.minecraft.stone", "item.minecraft.stone_sword"), keys);
    }

    @SuppressWarnings("deprecation")
    @Test
    void modernEnchantmentKeysMapToLegacyBukkitNames() {
        TranslationIndex<Enchantment> index = BukkitLocaleTranslations.wrap(
            new FakeLocaleTranslations(Collections.singletonMap("enchantment.minecraft.sharpness", "Острота"))
        ).enchantments();
        assertEquals(Enchantment.DAMAGE_ALL, index.findExact("Острота").get(0).getValue());
    }
}
