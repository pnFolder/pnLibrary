package org.pnfolder.pnlibrary.bukkit.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Stores complete Bukkit items as Base64, preserving metadata supported by the server.
 * Decode only trusted payloads: the format uses Java object deserialization.
 */
final class ItemStackCodec {
    private ItemStackCodec() {
    }

    /** Encodes {@code item}; invalid or unsupported items cause {@link IllegalArgumentException}. */
    static String encode(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot encode Bukkit item", exception);
        }
    }

    /** Reads an item produced by {@link #encode}; rejects blank, malformed, and non-item payloads. */
    static ItemStack decode(String encoded) {
        if (encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("Encoded item must not be blank");
        }
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
                Object read = input.readObject();
                if (!(read instanceof ItemStack)) {
                    throw new IllegalArgumentException("Encoded payload does not contain an ItemStack");
                }
                return (ItemStack) read;
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot decode Bukkit item", exception);
        }
    }
}
