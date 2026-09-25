package ru.privatenull.pnlibrary.api.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

class RemotePolicyContextTest {
    @Test
    fun `platform matching ignores case and punctuation`() {
        val platform = PlatformInfo(PlatformType.BUKKIT, "Purpur-Server", "1.21.4")

        assertEquals("purpurserver", platform.key)
        assert(platform.isNamed("PURPUR server"))
    }

    @Test
    fun `native platform access is typed`() {
        val native = Any()
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21.4"))
            .server(ServerInfo("1.21.4", MinecraftVersion.parseInfo("1.21.4")))
            .nativeHandle(native)
            .build()

        assertSame(native, context.requireNative(Any::class.java))
    }

    @Test
    fun `builder rejects blank custom keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePolicyContext.builder().value(" ", "invalid")
        }
    }

    @Test
    fun `builder trims keys and snapshots custom values`() {
        val source = linkedMapOf(" environment " to "production")
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21.4"))
            .server(ServerInfo("1.21.4", MinecraftVersion.parseInfo("1.21.4")))
            .values(source)
            .build()

        source.clear()
        assertEquals("production", context.values["environment"])
        assertThrows(UnsupportedOperationException::class.java) {
            (context.values as MutableMap)["another"] = "value"
        }
    }
}
