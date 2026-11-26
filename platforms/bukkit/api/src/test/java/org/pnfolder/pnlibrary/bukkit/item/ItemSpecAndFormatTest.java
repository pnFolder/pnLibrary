package org.pnfolder.pnlibrary.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ItemSpecAndFormatTest {
    @Test
    void aNewSpecIsOneStone() {
        ItemSpec spec = ItemSpec.builder().build();

        assertEquals("STONE", spec.getMaterial());
        assertEquals(1, spec.getAmount());
        assertNull(spec.getName());
        assertTrue(spec.getLore().isEmpty());
    }

    @Test
    void valuesAreTrimmedAndLoreIsReplacedOrExtended() {
        ItemSpec spec = ItemSpec.builder()
            .material(" DIAMOND_SWORD ")
            .lore("a", "b")
            .addLore("c")
            .enchant(" sharpness ", 5)
            .texture("  ")
            .build();

        assertEquals("DIAMOND_SWORD", spec.getMaterial());
        assertEquals(Arrays.asList("a", "b", "c"), spec.getLore());
        assertEquals(Collections.singletonMap("sharpness", 5), spec.getEnchantments());
        assertNull(spec.getTexture());
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().material(" "));
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().amount(0));
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().enchant("", 1));
    }

    @Test
    void aBuiltSpecDoesNotChangeWhenItsBuilderDoes() {
        ItemSpec.Builder builder = ItemSpec.builder().lore("a");
        ItemSpec spec = builder.build();
        builder.addLore("b");

        assertEquals(Collections.singletonList("a"), spec.getLore());
        assertThrows(UnsupportedOperationException.class, () -> spec.getLore().add("x"));
    }

    @Test
    void toBuilderKeepsEverythingAndCustomizersRunInOrder() {
        List<String> seen = new ArrayList<>();
        ItemSpec spec = ItemSpec.builder()
            .material("APPLE")
            .amount(3)
            .name("n")
            .enchant("sharpness", 2)
            .customize(item -> seen.add("first"))
            .customize(item -> seen.add("second"))
            .build();

        ItemSpec copy = spec.toBuilder().amount(5).build();

        assertEquals("APPLE", copy.getMaterial());
        assertEquals(5, copy.getAmount());
        assertEquals(3, spec.getAmount());
        assertEquals("n", copy.getName());
        assertEquals(Collections.singletonMap("sharpness", 2), copy.getEnchantments());
        assertEquals(2, copy.getCustomizers().size());
    }

    @Test
    void theDefaultFormatHasTheDocumentedKeys() {
        ItemFormat format = ItemFormat.DEFAULT;

        assertEquals("material", format.getMaterial());
        assertEquals("amount", format.getAmount());
        assertEquals("name", format.getName());
        assertEquals("lore", format.getLore());
        assertEquals("enchantments", format.getEnchantments());
        assertEquals(Arrays.asList("base64", "texture"), format.getTextures());
        assertEquals("item_data", format.getExact());
    }

    @Test
    void aFormatChangesOnlyTheKeysItNamesAndKeepsItsOwnFields() {
        ItemFormat format = ItemFormat.builder()
            .name(" display-name ")
            .texture("skin")
            .field("glow", (item, value) -> { })
            .build();

        assertEquals("display-name", format.getName());
        assertEquals("material", format.getMaterial());
        assertEquals(Collections.singletonList("skin"), format.getTextures());
        assertEquals(Collections.singletonList("glow"), new ArrayList<>(format.getFields().keySet()));

        ItemFormat copy = format.toBuilder().lore("description").build();
        assertEquals("description", copy.getLore());
        assertEquals("display-name", copy.getName());
        assertEquals(Collections.singletonList("glow"), new ArrayList<>(copy.getFields().keySet()));
    }

    @Test
    void aFormatRefusesBlankKeysAndAnEmptyTextureList() {
        assertThrows(IllegalArgumentException.class, () -> ItemFormat.builder().material(" "));
        assertThrows(IllegalArgumentException.class, () -> ItemFormat.builder().texture());
    }
}
