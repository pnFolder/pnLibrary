package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.net.URI
import java.net.URL
import java.util.Base64

/** Creates player heads from a Minecraft texture hash, URL, or Base64 texture payload. */
internal object PlayerHeads {
    private const val TEXTURES = "https://textures.minecraft.net/texture/"
    private const val FINISHED_CAPACITY = 128
    private val textureUrl = Regex("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+")
    private val textureHash = Regex("[A-Fa-f0-9]{32,}")
    private val modern by lazy { ModernProfileSetter.resolve(Reflection.Runtime) }
    private val legacy by lazy { LegacyProfileSetter.resolve(Reflection.Runtime) }

    /** Finished heads by texture URL; callers get clones so nobody changes a shared stack. */
    private val finished = CopyingCache<ItemStack>(FINISHED_CAPACITY) { it.clone() }

    /** Invalid textures produce an untextured head. [displayName] accepts legacy color codes. */
    @JvmStatic
    fun create(texture: String?, displayName: String?): ItemStack {
        val head = skinUrl(normalizeTexture(texture))?.let(::textured) ?: playerHead()
        if (displayName == null) return head
        val meta = head.itemMeta ?: return head
        meta.setDisplayName(ItemText.color(displayName))
        head.itemMeta = meta
        return head
    }

    /** A head wearing the skin published under texture [hash], or `null` when this server cannot apply skins. */
    fun textured(hash: String): ItemStack? = textured(URI(TEXTURES + hash).toURL())

    private fun textured(url: URL): ItemStack? {
        val key = url.toString()
        finished[key]?.let { return it }
        val head = playerHead()
        val meta = head.itemMeta as? SkullMeta ?: return null
        if (!applied(meta, url)) return null
        head.itemMeta = meta
        finished[key] = head
        return head
    }

    // 1.18.1+ has the Bukkit profile API; older servers take an authlib GameProfile in a private field.
    private fun applied(meta: SkullMeta, url: URL): Boolean =
        modern?.let { tryProfile { it.apply(meta, url) } } == true || legacy?.let { tryProfile { it.apply(meta, url) } } == true

    /** Removes an optional Base64 prefix and returns null for unsupported texture values. */
    @JvmStatic
    fun normalizeTexture(input: String?): String? {
        var value = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("base64:", true) || value.startsWith("base64-", true)) {
            value = value.substring(7).trim()
        }
        if (textureUrl.matches(value) || textureHash.matches(value)) return value
        return try {
            value.takeIf { textureUrl.containsMatchIn(decodeTexture(value)) }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun playerHead(): ItemStack {
        Material.matchMaterial("PLAYER_HEAD")?.let { return ItemStack(it) }
        return ItemStack(Material.matchMaterial("SKULL_ITEM") ?: Material.STONE, 1, 3.toShort())
    }

    /** Each server version supports one of the two profile APIs; a failure means "try the other one". */
    private inline fun tryProfile(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    } catch (_: LinkageError) {
        false
    }

    private fun skinUrl(value: String?): URL? {
        if (value == null) return null
        return when {
            textureUrl.matches(value) -> URI(value).toURL()
            textureHash.matches(value) -> URI(TEXTURES + value).toURL()
            else -> textureUrl.find(decodeTexture(value))?.value?.let { URI(it).toURL() }
        }
    }

    private fun decodeTexture(value: String): String = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
}
