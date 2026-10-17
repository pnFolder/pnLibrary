package ru.privatenull.pnlibrary.item

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Stores complete Bukkit items as Base64, preserving metadata supported by the server.
 * Decode only trusted payloads: the format uses Java object deserialization.
 */
object ItemStackCodec {
    /** Encodes [item]; invalid or unsupported items cause [IllegalArgumentException]. */
    @JvmStatic
    fun encode(item: ItemStack?): String {
        requireNotNull(item) { "Item must not be null" }
        return try {
            val bytes = ByteArrayOutputStream()
            BukkitObjectOutputStream(bytes).use { it.writeObject(item) }
            Base64.getEncoder().encodeToString(bytes.toByteArray())
        } catch (exception: Exception) {
            throw IllegalArgumentException("Cannot encode Bukkit item", exception)
        }
    }

    /** Reads an item produced by [encode]; rejects blank, malformed, and non-item payloads. */
    @JvmStatic
    fun decode(encoded: String?): ItemStack {
        require(!encoded.isNullOrBlank()) { "Encoded item must not be blank" }
        return try {
            val bytes = ByteArrayInputStream(Base64.getDecoder().decode(encoded))
            BukkitObjectInputStream(bytes).use { input ->
                input.readObject() as? ItemStack
                    ?: throw IllegalArgumentException("Encoded payload does not contain an ItemStack")
            }
        } catch (exception: Exception) {
            throw IllegalArgumentException("Cannot decode Bukkit item", exception)
        }
    }
}
