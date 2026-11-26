package org.pnfolder.pnlibrary.bukkit.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/** Reads an {@link ItemSpec} from configuration values in the keys an {@link ItemFormat} names. */
final class ItemSpecReader {
    private ItemSpecReader() {
    }

    static ItemSpec read(Map<?, ?> values, ItemFormat format) {
        if (values == null) {
            return null;
        }
        ItemSpec.Builder spec = ItemSpec.builder();
        readMaterial(spec, values, format);
        spec.amount(Math.max(integer(values.get(format.getAmount())), 1));
        Object name = values.get(format.getName());
        spec.name(name == null ? null : name.toString());
        readLore(spec, values.get(format.getLore()));
        for (Map.Entry<?, ?> entry : enchantments(values.get(format.getEnchantments())).entrySet()) {
            spec.enchant(entry.getKey().toString(), integer(entry.getValue()));
        }
        Object exact = values.get(format.getExact());
        spec.exact(exact == null ? null : exact.toString());
        for (Map.Entry<String, ItemField> field : format.getFields().entrySet()) {
            Object value = values.get(field.getKey());
            if (value != null) {
                field.getValue().read(spec, value);
            }
        }
        return spec.build();
    }

    static boolean hasBase64Prefix(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("base64-") || lower.startsWith("base64:");
    }

    private static void readMaterial(ItemSpec.Builder spec, Map<?, ?> values, ItemFormat format) {
        String material = text(values.get(format.getMaterial()));
        if (material != null) {
            if (hasBase64Prefix(material)) {
                spec.texture(material);
            } else {
                spec.material(material);
            }
        }
        for (String key : format.getTextures()) {
            String texture = text(values.get(key));
            if (texture != null) {
                spec.texture(texture);
                break;
            }
        }
    }

    private static void readLore(ItemSpec.Builder spec, Object lore) {
        if (lore instanceof List) {
            List<String> lines = new ArrayList<>();
            for (Object line : (List<?>) lore) {
                if (line instanceof String) {
                    lines.add((String) line);
                }
            }
            spec.lore(lines);
        } else if (lore instanceof String) {
            spec.lore((String) lore);
        }
    }

    private static Map<?, ?> enchantments(Object raw) {
        if (raw instanceof ConfigurationSection) {
            return ((ConfigurationSection) raw).getValues(false);
        }
        if (raw instanceof Map) {
            return (Map<?, ?>) raw;
        }
        return Collections.emptyMap();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int integer(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException error) {
            return 1;
        }
    }
}
