package org.pnfolder.pnlibrary.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class PolicyCompilersTest {
    @TempDir
    lateinit var directory: Path

    private fun build(data: ByteArray) = EcjBuild(
        "0.0.0", data.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) },
    )

    @Test
    fun `the jar is downloaded once and reused`() {
        val data = "jar".toByteArray()
        var downloads = 0
        val jar = EcjJar(directory, build(data)) { downloads++; ByteArrayInputStream(data) }
        assertEquals(jar.file(), jar.file())
        assertEquals(1, downloads)
    }

    @Test
    fun `a file with the wrong hash is rejected and not kept`() {
        val jar = EcjJar(directory, build("jar".toByteArray())) { ByteArrayInputStream("evil".toByteArray()) }
        assertThrows(IllegalStateException::class.java) { jar.file() }
        assertFalse(Files.exists(directory.resolve("ecj-0.0.0.jar")))
        Files.list(directory).use { assertEquals(0, it.count()) }
    }

    @Test
    fun `a damaged cached jar is downloaded again`() {
        val data = "jar".toByteArray()
        Files.write(directory.resolve("ecj-0.0.0.jar"), "broken".toByteArray())
        val jar = EcjJar(directory, build(data)) { ByteArrayInputStream(data) }
        assertEquals("jar", String(Files.readAllBytes(jar.file())))
    }

    @Test
    fun `old Java gets the old ECJ and new Java the new one`() {
        assertEquals(EcjBuild.LEGACY, EcjBuild.forJava(8))
        assertEquals(EcjBuild.LEGACY, EcjBuild.forJava(11))
        assertEquals(EcjBuild.MODERN, EcjBuild.forJava(17))
        assertEquals(EcjBuild.MODERN, EcjBuild.forJava(25))
    }

    @Test
    fun `an unreachable download is reported as a missing compiler`() {
        val jar = EcjJar(directory, build("jar".toByteArray())) { throw java.net.UnknownHostException("repo1.maven.org") }
        val failure = assertThrows(IllegalStateException::class.java) {
            PolicyCompilers(jar) { null }.compile(directory.resolve("A.java"), directory, "")
        }
        assertTrue(RemotePolicyFailureInterpreter.isEnvironment(failure))
    }

    @Test
    fun `ECJ compiles a policy when the server has no javac`() {
        val ecj = EcjJar(directory, EcjBuild.forJava(JavaRuntime.feature))
        val fetched = runCatching { ecj.file() }
        fetched.getOrThrow()
        val engine = RemotePolicyEngine(PolicyCompilers(ecj) { null })
        val source = """
            package example;
            import org.pnfolder.pnlibrary.api.remote.*;
            public final class EcjPolicy implements RemotePolicy {
                public RemotePolicyResult check(RemotePolicyContext context) {
                    return RemotePolicyResult.deny("denied by ecj");
                }
            }
        """.trimIndent().toByteArray()
        val context = RemotePolicyContext.builder()
            .product(ProductInfo("demo", "Demo", "1.0.0"))
            .platform(PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21"))
            .server(ServerInfo("1.21", MinecraftVersion.parseInfo(null)))
            .build()
        assertEquals("denied by ecj", engine.checkSource(source, context).message)
    }
}
