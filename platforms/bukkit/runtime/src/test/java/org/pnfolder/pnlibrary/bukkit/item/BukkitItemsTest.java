package org.pnfolder.pnlibrary.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

class BukkitItemsTest {
    @Test
    void aFormatRenamesTheKeysAndReadsFieldsOfItsOwnIntoTheSameSpec() {
        List<Object> seen = new ArrayList<>();
        ItemFormat format = ItemFormat.builder()
            .material("type")
            .name("display-name")
            .lore("description")
            .enchantments("magic")
            .field("glow", (item, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    item.enchant("unbreaking", 1);
                }
            })
            .field("model", (item, value) -> item.customize(stack -> seen.add(value)))
            .build();
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("type", "DIAMOND_SWORD");
        config.set("amount", 3);
        config.set("display-name", "&bМеч");
        config.set("description", "&7одна строка");
        config.set("magic.sharpness", 5);
        config.set("glow", true);
        config.set("model", 42);
        config.set("name", "не тот ключ");

        ItemSpec spec = BukkitItems.INSTANCE.read(config, format);

        assertEquals("DIAMOND_SWORD", spec.getMaterial());
        assertEquals(3, spec.getAmount());
        assertEquals("&bМеч", spec.getName());
        assertEquals(Collections.singletonList("&7одна строка"), spec.getLore());
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        enchantments.put("sharpness", 5);
        enchantments.put("unbreaking", 1);
        assertEquals(enchantments, spec.getEnchantments());
        assertEquals(1, spec.getCustomizers().size());
        spec.getCustomizers().get(0).apply(new TestItem(null, 1));
        assertEquals(Collections.singletonList(42), seen);
    }

    @Test
    void theDefaultFormatKeepsItsKeysAndABase64MaterialBecomesATexture() {
        Map<String, Object> values = new HashMap<>();
        values.put("material", "base64:abc");
        values.put("texture", "hash");
        values.put("lore", Arrays.asList("a", "b"));
        ItemSpec spec = BukkitItems.INSTANCE.read(values);
        assertEquals("hash", spec.getTexture());
        assertEquals(Arrays.asList("a", "b"), spec.getLore());
        assertEquals("STONE", spec.getMaterial());
    }

    @Test
    void aSpecMadeByHandCanBeChangedThroughItsBuilderWithoutTouchingTheOriginal() {
        ItemSpec base = ItemSpec.builder().material("APPLE").amount(2).name("&aЯблоко").lore("одна").build();
        ItemSpec changed = base.toBuilder().amount(5).addLore("вторая").build();
        assertEquals(2, base.getAmount());
        assertEquals(Collections.singletonList("одна"), base.getLore());
        assertEquals(5, changed.getAmount());
        assertEquals(Arrays.asList("одна", "вторая"), changed.getLore());
        assertEquals("APPLE", changed.getMaterial());
    }

    @Test
    void mapAndSectionApplyTheSameOverridesToDecodedItems() {
        for (boolean useSection : new boolean[] {false, true}) {
            ItemMeta meta = mock(ItemMeta.class);
            ItemStack item = new TestItem(meta, 4);
            try (MockedStatic<ItemStackCodec> codec = mockStatic(ItemStackCodec.class)) {
                codec.when(() -> ItemStackCodec.decode("saved")).thenReturn(item);
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("item_data", "saved");
                values.put("name", "&bSword");
                values.put("lore", Arrays.asList("&7Details"));
                ItemStack result;
                if (useSection) {
                    MemoryConfiguration config = new MemoryConfiguration();
                    for (Map.Entry<String, Object> entry : values.entrySet()) {
                        config.set(entry.getKey(), entry.getValue());
                    }
                    result = BukkitItems.INSTANCE.fromSection(config);
                } else {
                    result = BukkitItems.INSTANCE.fromMap(values);
                }

                assertSame(item, result);
                assertEquals(4, result.getAmount());
                verify(meta).setDisplayName("§bSword");
                verify(meta).setLore(Arrays.asList("§7Details"));
            }
        }
    }

    @Test
    void texturedHeadsRespectConfiguredAmount() {
        ItemStack head = new TestItem(null, 1);
        try (MockedStatic<PlayerHeads> heads = mockStatic(PlayerHeads.class)) {
            heads.when(() -> PlayerHeads.create("texture", "&fItem")).thenReturn(head);
            Map<String, Object> values = new HashMap<>();
            values.put("texture", "texture");
            values.put("amount", 12);
            assertEquals(12, BukkitItems.INSTANCE.fromMap(values).getAmount());
        }
    }

    @Test
    void exactWritingPreservesAmountAndLeavesSourceUntouched() {
        ItemStack source = new TestItem(null, 12);
        MemoryConfiguration parent = new MemoryConfiguration();
        try (MockedStatic<ItemStackCodec> codec = mockStatic(ItemStackCodec.class)) {
            codec.when(() -> ItemStackCodec.encode(ArgumentMatchers.any(ItemStack.class))).thenAnswer(invocation -> {
                assertEquals(12, invocation.<ItemStack>getArgument(0).getAmount());
                return "encoded-stack";
            });
            BukkitItems.INSTANCE.writeExact(parent, "reward", source);
        }
        assertEquals(12, source.getAmount());
        assertEquals(12, parent.getInt("reward.amount"));
        assertEquals("encoded-stack", parent.getString("reward.item_data"));
    }

    /** Keeps these unit tests independent of a live Bukkit server. */
    private static final class TestItem extends ItemStack {
        private final ItemMeta meta;

        TestItem(ItemMeta meta, int amount) {
            super(Material.STONE, amount);
            this.meta = meta;
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta;
        }

        @Override
        public boolean setItemMeta(ItemMeta itemMeta) {
            return true;
        }

        @Override
        public ItemStack clone() {
            return new TestItem(meta, getAmount());
        }
    }
}
