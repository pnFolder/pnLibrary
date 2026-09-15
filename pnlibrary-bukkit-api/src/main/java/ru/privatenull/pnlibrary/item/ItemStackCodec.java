package ru.privatenull.pnlibrary.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Encodes complete Bukkit item stacks for configuration and database storage.
 *
 * <p>The encoded payload retains item metadata, enchantments, custom model data,
 * persistent data, and server-specific metadata supported by the running Bukkit
 * implementation. Payloads must only be decoded by trusted server code.</p>
 */
public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    /**
     * Encodes one item stack as Base64.
     *
     * @param item item to encode
     * @return Base64 representation of the complete item
     * @throws IllegalArgumentException when the item cannot be serialized
     */
    public static String encode(ItemStack item) {
        if (item == null) throw new IllegalArgumentException("Item must not be null");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Cannot encode Bukkit item", exception);
        }
    }

    /**
     * Decodes an item produced by {@link #encode(ItemStack)}.
     *
     * @param encoded Base64 item payload
     * @return decoded item stack
     * @throws IllegalArgumentException when the payload is malformed or is not an item
     */
    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("Encoded item must not be blank");
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            Object value = input.readObject();
            if (value instanceof ItemStack) return (ItemStack) value;
            throw new IllegalArgumentException("Encoded payload does not contain an ItemStack");
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            throw new IllegalArgumentException("Cannot decode Bukkit item", exception);
        }
    }
}
