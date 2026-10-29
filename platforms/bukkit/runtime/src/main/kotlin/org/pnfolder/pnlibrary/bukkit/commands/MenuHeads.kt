package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Textured player heads and `&#RRGGBB` colours for the menu, built by reflection so the same jar works on 1.8 and on
 * current Paper. A head that cannot be made is reported as `null`, and the caller falls back to an ordinary item.
 */
internal object MenuHeads {
    private const val TEXTURES = "https://textures.minecraft.net/texture/"
    private val cache = ConcurrentHashMap<String, ItemStack>()
    private val hexPattern = Regex("&#([0-9A-Fa-f]{6})")

    /** True when the server's chat accepts `§x` hex colours (1.16 and newer). */
    val hexSupported: Boolean = runCatching { Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String::class.java) }.isSuccess

    /** A head carrying the skin whose texture [hash] is published on textures.minecraft.net, or `null`. */
    fun head(hash: String): ItemStack? = cache[hash]?.clone() ?: create(hash)?.also { cache[hash] = it.clone() }

    /** Turns `&#RRGGBB` and `&` codes of a template into section-sign codes, or the nearest legacy colour below 1.16. */
    fun color(template: String): String {
        val coloured = hexPattern.replace(template) { match ->
            val rgb = match.groupValues[1]
            if (hexSupported) "§x" + rgb.map { "§$it" }.joinToString("") else nearest(rgb)
        }
        return coloured.replace('&', '§')
    }

    /** [text] whose colour moves from [from] to [to] (both `RRGGBB`) letter by letter; one colour below 1.16. */
    fun gradient(text: String, from: String, to: String): String {
        if (!hexSupported || text.length < 2) return color("&#$from") + text
        val start = from.toInt(RADIX)
        val end = to.toInt(RADIX)
        return buildString {
            text.forEachIndexed { index, char ->
                val step = index.toDouble() / (text.length - 1)
                val rgb = intArrayOf(RED_SHIFT, GREEN_SHIFT, 0).map { shift ->
                    val a = start shr shift and 0xFF
                    val b = end shr shift and 0xFF
                    (a + (b - a) * step).toInt()
                }
                append(color("&#" + "%02X%02X%02X".format(rgb[0], rgb[1], rgb[2]))).append(char)
            }
        }
    }

    private fun create(hash: String): ItemStack? = runCatching {
        val stack = skull() ?: return null
        val meta = stack.itemMeta as? SkullMeta ?: return null
        val url = URL(TEXTURES + hash)
        if (!modern(meta, url) && !legacy(meta, url)) return null
        stack.itemMeta = meta
        stack
    }.getOrNull()

    private fun skull(): ItemStack? {
        Material.matchMaterial("PLAYER_HEAD")?.let { return ItemStack(it) }
        val legacy = Material.matchMaterial("SKULL_ITEM") ?: return null
        return ItemStack(legacy, 1, 3.toShort())
    }

    private fun modern(meta: SkullMeta, url: URL): Boolean = runCatching {
        val id = profileId(url)
        val profileType = Class.forName("org.bukkit.profile.PlayerProfile")
        val texturesType = Class.forName("org.bukkit.profile.PlayerTextures")
        val profile = Bukkit::class.java.getMethod("createPlayerProfile", UUID::class.java, String::class.java)
            .invoke(null, id, profileName(id))
        val textures = profileType.getMethod("getTextures").invoke(profile)
        texturesType.getMethod("setSkin", URL::class.java).invoke(textures, url)
        profileType.getMethod("setTextures", texturesType).invoke(profile, textures)
        SkullMeta::class.java.getMethod("setOwnerProfile", profileType).invoke(meta, profile)
        true
    }.getOrDefault(false)

    private fun legacy(meta: SkullMeta, url: URL): Boolean = runCatching {
        val id = profileId(url)
        val profileClass = Class.forName("com.mojang.authlib.GameProfile")
        val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
        val profile = profileClass.getConstructor(UUID::class.java, String::class.java).newInstance(id, profileName(id))
        val json = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val property = propertyClass.getConstructor(String::class.java, String::class.java).newInstance("textures", encoded)
        val properties = profileClass.getMethod("getProperties").invoke(profile)
        properties.javaClass.getMethod("put", Any::class.java, Any::class.java).invoke(properties, "textures", property)
        val field = generateSequence<Class<*>>(meta.javaClass) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredField("profile") }.getOrNull() }
        field.isAccessible = true
        field.set(meta, profile)
        true
    }.getOrDefault(false)

    private fun profileId(url: URL): UUID = UUID.nameUUIDFromBytes("pnLibrary:$url".toByteArray(StandardCharsets.UTF_8))

    private fun profileName(id: UUID): String = "pn" + id.toString().replace("-", "").take(PROFILE_NAME_LENGTH)

    private fun nearest(rgb: String): String {
        val value = rgb.toInt(RADIX)
        val red = value shr 16 and 0xFF
        val green = value shr 8 and 0xFF
        val blue = value and 0xFF
        return LEGACY.minByOrNull { (color, _) ->
            val dr = (color shr 16 and 0xFF) - red
            val dg = (color shr 8 and 0xFF) - green
            val db = (color and 0xFF) - blue
            dr * dr + dg * dg + db * db
        }!!.second
    }

    private const val RADIX = 16
    private const val PROFILE_NAME_LENGTH = 14
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    private val LEGACY = listOf(
        0x000000 to "§0", 0x0000AA to "§1", 0x00AA00 to "§2", 0x00AAAA to "§3", 0xAA0000 to "§4", 0xAA00AA to "§5",
        0xFFAA00 to "§6", 0xAAAAAA to "§7", 0x555555 to "§8", 0x5555FF to "§9", 0x55FF55 to "§a", 0x55FFFF to "§b",
        0xFF5555 to "§c", 0xFF55FF to "§d", 0xFFFF55 to "§e", 0xFFFFFF to "§f",
    )
}
