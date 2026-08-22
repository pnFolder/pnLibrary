package ru.privatenull.pnlibrary.item;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/** Canonical lossless ItemStack codec for YAML and database payloads. */
public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static ItemStack decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
