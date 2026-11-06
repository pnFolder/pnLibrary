package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.nio.file.Files

class PolicyCompilersTest {
    private val context = RemotePolicyContext.builder()
        .product(ProductInfo("demo", "Demo", "1.0.0"))
        .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21"))
        .server(ServerInfo("1.21", MinecraftVersion.parseInfo(null)))
        .build()

    private val source = """
        package example;
        import org.pnfolder.pnlibrary.api.remote.*;
        public final class EcjPolicy implements RemotePolicy {
            public RemotePolicyResult check(RemotePolicyContext context) {
                return RemotePolicyResult.deny("denied by ecj");
            }
        }
    """.trimIndent().toByteArray()

    @Test
    fun `ECJ compiles a policy when the server has no javac`() {
        val engine = RemotePolicyEngine(PolicyCompilers(systemCompiler = { null }))
        assertEquals("denied by ecj", engine.checkSource(source, context).message)
    }

    @Test
    fun `a JRE where ECJ was not loaded is reported as a missing compiler`() {
        val root = Files.createTempDirectory("pn-no-ecj")
        val bare = ClassLoader.getSystemClassLoader().parent
        val failure = assertThrows(IllegalStateException::class.java) {
            PolicyCompilers(systemCompiler = { null }, ecjLoader = bare).compile(root.resolve("A.java"), root, "")
        }
        assertTrue(RemotePolicyFailureInterpreter.isCompilerMissing(failure))
    }

    @Test
    fun `a broken policy reports its compile errors`() {
        val engine = RemotePolicyEngine(PolicyCompilers(systemCompiler = { null }))
        val broken = "public class Broken implements org.pnfolder.pnlibrary.api.remote.RemotePolicy { int x = ; }".toByteArray()
        val failure = assertThrows(IllegalArgumentException::class.java) { engine.checkSource(broken, context) }
        assertTrue(failure.message.orEmpty().startsWith("Remote policy compilation failed"))
    }
}
