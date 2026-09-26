package ru.privatenull.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

class RemotePolicyEngineTest {
    @Test fun `compiles a raw Java policy against the shared context`() {
        val source = """
            package example;
            import ru.privatenull.pnlibrary.api.remote.*;
            public final class Policy implements RemotePolicy {
                public RemotePolicyResult check(RemotePolicyContext context) {
                    return context.getPlatform().getType().name().equals("VELOCITY")
                        ? RemotePolicyResult.deny("update required") : RemotePolicyResult.allow();
                }
            }
        """.trimIndent().toByteArray()
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.VELOCITY, "Velocity", "3.3.0"))
            .server(ServerInfo("3.3.0", MinecraftVersion.parseInfo(null)))
            .build()
        val result = RemotePolicyEngine.checkSource(source, context)
        assertFalse(result.allowed)
        assertEquals("update required", result.message)
    }

    @Test fun `compiles a raw Kotlin policy against the shared context`() {
        val source = """
            package example
            import ru.privatenull.pnlibrary.api.remote.RemotePolicy
            import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
            import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
            import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult
            class KotlinPolicy : RemotePolicy {
                override fun check(context: RemotePolicyContext): RemotePolicyResult {
                    return if (context.product.version.startsWith("1."))
                        RemotePolicyResult.deny(RemotePolicyExplanation.builder("kotlin update required")
                            .branch("versions") { it.child(context.product.version).child("2.0.0") }
                            .build())
                    else RemotePolicyResult.allow()
                }
            }
        """.trimIndent().toByteArray()
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21.4"))
            .server(ServerInfo("1.21.4", MinecraftVersion.parseInfo("1.21.4")))
            .build()

        val result = RemotePolicyEngine.checkSource(source, context, "kt")

        assertFalse(result.allowed)
        assertEquals("kotlin update required", result.message)
        assertEquals("versions", result.explanation.children.single().text)
        assertEquals(listOf("1.0.0", "2.0.0"), result.explanation.children.single().children.map { it.text })
    }
}
