package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.inventory.meta.SkullMeta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI
import java.net.URL
import java.util.UUID

interface FakeTextures {
    fun setSkin(url: URL)
}

interface FakeProfile {
    fun getTextures(): FakeTextures

    fun setTextures(textures: FakeTextures)
}

interface FakeSkull {
    fun setOwnerProfile(profile: FakeProfile)
}

object FakeBukkit {
    val log = mutableListOf<String>()

    @JvmStatic
    fun createPlayerProfile(id: UUID, name: String): FakeProfile {
        log += "create $id $name"
        val textures = object : FakeTextures {
            override fun setSkin(url: URL) {
                log += "skin $url"
            }
        }
        return object : FakeProfile {
            override fun getTextures() = textures.also { log += "getTextures" }

            override fun setTextures(textures: FakeTextures) {
                log += "setTextures"
            }
        }
    }
}

class ProfileSettersTest {
    private class CountingReflection : Reflection {
        var lookups = 0
        private val types = mapOf(
            "org.bukkit.profile.PlayerProfile" to FakeProfile::class.java,
            "org.bukkit.profile.PlayerTextures" to FakeTextures::class.java,
        )

        override fun type(name: String): Class<*> = types[name] ?: throw ClassNotFoundException(name)

        override fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method {
            lookups++
            return type.getMethod(name, *parameters)
        }
    }

    private val urls = (1..100).map { URI("https://textures.minecraft.net/texture/hash$it").toURL() }

    private fun skull(): SkullMeta = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(SkullMeta::class.java, FakeSkull::class.java),
    ) { _, method, _ ->
        if (method.name == "setOwnerProfile") FakeBukkit.log += "owner"
        null
    } as SkullMeta

    /** The way heads were made before: every class and method looked up again for each head. */
    private fun oldWay(reflection: CountingReflection, url: URL, meta: SkullMeta) {
        val id = UUID.nameUUIDFromBytes("pnLibrary:$url".toByteArray(Charsets.UTF_8))
        val name = "pn" + id.toString().replace("-", "").take(14)
        val profileType = reflection.type("org.bukkit.profile.PlayerProfile")
        val texturesType = reflection.type("org.bukkit.profile.PlayerTextures")
        val profile = reflection.method(FakeBukkit::class.java, "createPlayerProfile", UUID::class.java, String::class.java).invoke(null, id, name)
        val textures = reflection.method(profileType, "getTextures").invoke(profile)
        reflection.method(texturesType, "setSkin", URL::class.java).invoke(textures, url)
        reflection.method(profileType, "setTextures", texturesType).invoke(profile, textures)
        reflection.method(FakeSkull::class.java, "setOwnerProfile", profileType).invoke(meta, profile)
    }

    @Test
    fun `the setter makes the same calls as the lookup on every head did`() {
        val reflection = CountingReflection()
        val setter = ModernProfileSetter.resolve(reflection, FakeBukkit::class.java, FakeSkull::class.java)
        assertNotNull(setter)

        FakeBukkit.log.clear()
        urls.forEach { oldWay(reflection, it, skull()) }
        val before = FakeBukkit.log.toList()
        FakeBukkit.log.clear()
        urls.forEach { setter!!.apply(skull(), it) }

        assertEquals(before, FakeBukkit.log.toList())
    }

    @Test
    fun `a hundred heads look up each method once instead of once per head`() {
        val old = CountingReflection()
        urls.forEach { oldWay(old, it, skull()) }

        val fresh = CountingReflection()
        val setter = ModernProfileSetter.resolve(fresh, FakeBukkit::class.java, FakeSkull::class.java)!!
        urls.forEach { setter.apply(skull(), it) }

        assertEquals(500, old.lookups)
        assertEquals(5, fresh.lookups)
    }

    @Test
    fun `a server without the profile API gives no setter after a single failed lookup`() {
        val reflection = object : Reflection {
            var types = 0
            override fun type(name: String): Class<*> = throw ClassNotFoundException(name).also { types++ }

            override fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method = error("unreachable")
        }
        assertNull(ModernProfileSetter.resolve(reflection))
        assertNull(LegacyProfileSetter.resolve(reflection))
        assertEquals(2, reflection.types)
    }
}
