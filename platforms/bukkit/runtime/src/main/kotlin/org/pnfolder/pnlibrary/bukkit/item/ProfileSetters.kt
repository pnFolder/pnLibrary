package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.Bukkit
import org.bukkit.inventory.meta.SkullMeta
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Looks up classes and methods by name; a seam so tests can count the lookups. */
internal interface Reflection {
    fun type(name: String): Class<*>

    fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method

    object Runtime : Reflection {
        override fun type(name: String): Class<*> = Class.forName(name)

        override fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method = type.getMethod(name, *parameters)
    }
}

/** Puts a skin from a texture URL on a skull; each server version supports one of the two implementations. */
internal interface ProfileSetter {
    fun apply(meta: SkullMeta, url: URL)
}

/** Profile ids and names derived from the texture URL, so one texture always gives the same profile. */
private object ProfileNames {
    fun id(url: URL): UUID = UUID.nameUUIDFromBytes("pnLibrary:$url".toByteArray(Charsets.UTF_8))

    fun name(id: UUID): String = "pn" + id.toString().replace("-", "").take(14)
}

/** Bukkit profile API (1.18.1+). Every class and method is looked up once, when the setter is made. */
internal class ModernProfileSetter private constructor(
    private val createProfile: Method,
    private val getTextures: Method,
    private val setSkin: Method,
    private val setTextures: Method,
    private val setOwnerProfile: Method,
) : ProfileSetter {
    override fun apply(meta: SkullMeta, url: URL) {
        val id = ProfileNames.id(url)
        val profile = createProfile.invoke(null, id, ProfileNames.name(id))
        val textures = getTextures.invoke(profile)
        setSkin.invoke(textures, url)
        setTextures.invoke(profile, textures)
        setOwnerProfile.invoke(meta, profile)
    }

    companion object {
        /** The setter, or null when this server has no profile API. */
        fun resolve(
            reflection: Reflection,
            bukkit: Class<*> = Bukkit::class.java,
            skull: Class<*> = SkullMeta::class.java,
        ): ModernProfileSetter? = try {
            val profileType = reflection.type("org.bukkit.profile.PlayerProfile")
            val texturesType = reflection.type("org.bukkit.profile.PlayerTextures")
            ModernProfileSetter(
                reflection.method(bukkit, "createPlayerProfile", UUID::class.java, String::class.java),
                reflection.method(profileType, "getTextures"),
                reflection.method(texturesType, "setSkin", URL::class.java),
                reflection.method(profileType, "setTextures", texturesType),
                reflection.method(skull, "setOwnerProfile", profileType),
            )
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}

/** Older servers: an authlib GameProfile written into the private `profile` field of the skull. */
internal class LegacyProfileSetter private constructor(
    private val newProfile: Constructor<*>,
    private val getProperties: Method,
    private val putProperty: Method,
    private val newProperty: Constructor<*>,
) : ProfileSetter {
    private val fields = ConcurrentHashMap<Class<*>, Field>()

    override fun apply(meta: SkullMeta, url: URL) {
        val id = ProfileNames.id(url)
        val profile = newProfile.newInstance(id, ProfileNames.name(id))
        val payload = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
        val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        putProperty.invoke(getProperties.invoke(profile), "textures", newProperty.newInstance("textures", encoded))
        fields.computeIfAbsent(meta.javaClass, ::profileField).set(meta, profile)
    }

    private fun profileField(type: Class<*>): Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField("profile").apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("profile")
    }

    companion object {
        /** The setter, or null when this server has no authlib. */
        fun resolve(reflection: Reflection): LegacyProfileSetter? = try {
            val profileType = reflection.type("com.mojang.authlib.GameProfile")
            val propertyType = reflection.type("com.mojang.authlib.properties.Property")
            val getProperties = reflection.method(profileType, "getProperties")
            LegacyProfileSetter(
                profileType.getConstructor(UUID::class.java, String::class.java),
                getProperties,
                reflection.method(getProperties.returnType, "put", Any::class.java, Any::class.java),
                propertyType.getConstructor(String::class.java, String::class.java),
            )
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
