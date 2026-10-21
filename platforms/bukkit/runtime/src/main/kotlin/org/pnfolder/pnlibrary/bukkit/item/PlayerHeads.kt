package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.lang.reflect.Field
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.UUID

/** Creates player heads from a Minecraft texture hash, URL, or Base64 texture payload. */
internal object PlayerHeads {
    private val textureUrl = Regex("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+")
    private val textureHash = Regex("[A-Fa-f0-9]{32,}")

    /** Invalid textures produce an untextured head. [displayName] accepts legacy color codes. */
    @JvmStatic
    fun create(texture: String?, displayName: String?): ItemStack {
        val head = playerHead()
        val meta = head.itemMeta as? SkullMeta ?: return head
        if (displayName != null) meta.setDisplayName(ItemText.color(displayName))
        skinUrl(normalizeTexture(texture))?.let { url ->
            if (!applyModernProfile(meta, url)) applyLegacyProfile(meta, url)
        }
        head.itemMeta = meta
        return head
    }

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

    private fun applyModernProfile(meta: SkullMeta, url: URL): Boolean = tryProfile {
        val id = profileId(url)
        val profileType = Class.forName("org.bukkit.profile.PlayerProfile")
        val texturesType = Class.forName("org.bukkit.profile.PlayerTextures")
        val profile = Bukkit::class.java.getMethod("createPlayerProfile", UUID::class.java, String::class.java)
            .invoke(null, id, profileName(id))
        val textures = profileType.getMethod("getTextures").invoke(profile)
        texturesType.getMethod("setSkin", URL::class.java).invoke(textures, url)
        profileType.getMethod("setTextures", texturesType).invoke(profile, textures)
        SkullMeta::class.java.getMethod("setOwnerProfile", profileType).invoke(meta, profile)
    }

    private fun applyLegacyProfile(meta: SkullMeta, url: URL): Boolean = tryProfile {
        val id = profileId(url)
        val profileType = Class.forName("com.mojang.authlib.GameProfile")
        val profile = profileType.getConstructor(UUID::class.java, String::class.java)
            .newInstance(id, profileName(id))
        val properties = profileType.getMethod("getProperties").invoke(profile)
        val propertyType = Class.forName("com.mojang.authlib.properties.Property")
        val payload = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val property = propertyType.getConstructor(String::class.java, String::class.java)
            .newInstance("textures", encoded)
        properties.javaClass.getMethod("put", Any::class.java, Any::class.java)
            .invoke(properties, "textures", property)
        profileField(meta.javaClass).apply { isAccessible = true }.set(meta, profile)
    }

    /** Server versions expose different profile APIs; try the next supported representation. */
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

    private fun profileField(type: Class<*>): Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField("profile")
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("profile")
    }

    private fun skinUrl(value: String?): URL? {
        if (value == null) return null
        return when {
            textureUrl.matches(value) -> URI(value).toURL()
            textureHash.matches(value) -> URI("https://textures.minecraft.net/texture/$value").toURL()
            else -> textureUrl.find(decodeTexture(value))?.value?.let { URI(it).toURL() }
        }
    }

    private fun decodeTexture(value: String): String = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    private fun profileId(url: URL): UUID = UUID.nameUUIDFromBytes("pnLibrary:$url".toByteArray(Charsets.UTF_8))
    private fun profileName(id: UUID): String = "pn" + id.toString().replace("-", "").take(14)
}
