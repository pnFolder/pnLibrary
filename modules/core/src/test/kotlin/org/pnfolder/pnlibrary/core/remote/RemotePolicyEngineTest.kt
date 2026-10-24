package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

class RemotePolicyEngineTest {
    @Test
    fun `pinned source is rejected before parsing or execution when bytes differ`() {
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21.4"))
            .server(ServerInfo("1.21.4", MinecraftVersion.parseInfo("1.21.4")))
            .value("pnlibrary.policy.sha256", "0".repeat(64)).build()
        val error = assertThrows<IllegalArgumentException> { RemotePolicyEngine.checkSource("untrusted".toByteArray(), context) }
        assertTrue(error.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun `compiles a raw Java policy against the shared context`() {
        val source = """
            package example;
            import org.pnfolder.pnlibrary.api.remote.*;
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
            .value("pnlibrary.policy.sha256", Digests.sha256(source))
            .build()
        val result = RemotePolicyEngine.checkSource(source, context)
        assertFalse(result.allowed)
        assertEquals("update required", result.message)
    }
}
