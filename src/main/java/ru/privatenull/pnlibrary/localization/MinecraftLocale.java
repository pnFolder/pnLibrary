package ru.privatenull.pnlibrary.localization;

import java.util.Locale;

/** Bundled Minecraft translation tables supported by pnLibrary. */
public enum MinecraftLocale {
    RU_RU("ru_ru", "Предмет", "Зачарование", "Неизвестное зачарование", "усиленное", "длительное"),
    EN_US("en_us", "Item", "Enchantment", "Unknown enchantment", "upgraded", "extended");

    private final String id;
    private final String itemFallback;
    private final String enchantmentFallback;
    private final String unknownEnchantment;
    private final String upgraded;
    private final String extended;

    MinecraftLocale(String id, String itemFallback, String enchantmentFallback,
                    String unknownEnchantment, String upgraded, String extended) {
        this.id = id;
        this.itemFallback = itemFallback;
        this.enchantmentFallback = enchantmentFallback;
        this.unknownEnchantment = unknownEnchantment;
        this.upgraded = upgraded;
        this.extended = extended;
    }

    public String id() {
        return id;
    }

    String itemFallback() {
        return itemFallback;
    }

    String enchantmentFallback() {
        return enchantmentFallback;
    }

    String unknownEnchantment() {
        return unknownEnchantment;
    }

    String upgraded() {
        return upgraded;
    }

    String extended() {
        return extended;
    }

    public static MinecraftLocale parse(String value) {
        if (value == null || value.isBlank()) return RU_RU;
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "en", "en_us", "english" -> EN_US;
            case "ru", "ru_ru", "russian", "русский" -> RU_RU;
            default -> throw new IllegalArgumentException("Unsupported Minecraft locale: " + value);
        };
    }
}
