package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.*
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources
import org.pnfolder.pnlibrary.core.remote.PolicyDeniedException
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeRemotePolicy
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class RuntimeRemotePolicyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `validation preserves every policy setting including optional support and error actions`() {
        for (action in PolicyErrorAction.values()) {
            for (support in listOf(null, "https://example.org/support")) {
                val policy = RemotePolicySettings(
                    source = directory.resolve("Policy.java").toUri().toString(),
                    checkEvery = Duration.ofMinutes(15),
                    onDeny = DenyAction.DISABLE_PLUGIN,
                    values = mapOf("product" to "pnlibrary"),
                    onError = action,
                    supportUrl = support,
                )

                assertEquals(policy, RuntimeRemotePolicy.validated(policy), "action=$action, support=$support")
            }
        }
    }

    @Test
    fun `library policy is checked at start, then scheduled once after readiness with library owned tasks`() {
        val fixture = fixture(PlatformType.BUKKIT)
        val policy = policy("AllowPolicy", true)
        fixture.start(policy)
        assertNull(fixture.task)
        fixture.ready.single().run()
        fixture.ready.single().run()
        assertEquals(policy.checkEvery, fixture.task!!.interval)
        assertEquals(policy.checkEvery, fixture.task!!.delay)
        assertEquals(TaskExecution.Kind.ASYNC, fixture.task!!.execution.kind)
        verify(fixture.tasks, times(1)).schedule(policyArgument(TaskSpec::class.java))
        verify(fixture.platform, atLeastOnce()).remotePolicyContext(fixture.owner, policy.values)
        verify(fixture.library, never()).plugins
    }

    @Test
    fun `a denial stops startup before the plugin reports itself enabled on all three platforms`() {
        for (type in listOf(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)) {
            val fixture = fixture(type)
            assertThrows(PolicyDeniedException::class.java) { fixture.start(policy("Denied${type.name}", false)) }
            assertTrue(fixture.ready.isEmpty(), "$type schedules nothing after a denial")
            assertNull(fixture.task)
            verify(fixture.platform).disableDependents(fixture.owner)
            verify(fixture.platform).disableOwner(fixture.owner)
        }
    }

    @Test
    fun `an allowed policy keeps the plugin running on all three platforms`() {
        for (type in listOf(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)) {
            val fixture = fixture(type)
            fixture.start(policy("Allowed${type.name}", true))
            fixture.ready.single().run()
            fixture.task!!.action.run(mock(TaskContext::class.java))
            assertEquals(0, fixture.closes, "$type")
            verify(fixture.platform, never()).disableOwner(fixture.owner)
        }
    }

    @Test
    fun `a platform that guards dependents switches the owner off itself`() {
        val fixture = fixture(PlatformType.BUKKIT)
        `when`(fixture.platform.disableDependents(fixture.owner)).thenReturn(true)
        assertThrows(PolicyDeniedException::class.java) { fixture.start(policy("GuardedPolicy", false)) }
        verify(fixture.platform).disableDependents(fixture.owner)
        verify(fixture.platform, never()).disableOwner(fixture.owner)
    }

    @Test
    fun `a later denial disables the owner and closes the runtime only without native support`() {
        val fixture = fixture(PlatformType.BUKKIT)
        val file = directory.resolve("FlipPolicy.java")
        val allow = policy("FlipPolicy", true)
        fixture.start(allow)
        fixture.ready.single().run()
        Files.write(file, Files.readAllBytes(file).toString(Charsets.UTF_8)
            .replace("RemotePolicyResult.allow()", "RemotePolicyResult.deny(\"No longer supported\")").toByteArray())
        fixture.task!!.action.run(mock(TaskContext::class.java))
        verify(fixture.platform).disableOwner(fixture.owner)
        assertEquals(1, fixture.closes)
    }

    @Test
    fun `a policy that cannot be checked keeps the library running when told to`() {
        val missing = RemotePolicySettings.builder().source(directory.resolve("Missing.java").toUri().toString())
            .onError(PolicyErrorAction.KEEP_RUNNING).build()
        val fixture = fixture(PlatformType.BUKKIT)
        fixture.start(missing)
        fixture.ready.single().run()
        fixture.task!!.action.run(mock(TaskContext::class.java))
        assertEquals(0, fixture.closes)
        verify(fixture.platform, never()).disableOwner(fixture.owner)
    }

    @Test
    fun `a policy that cannot be checked at start stops the plugin, but after one success a failure only warns`() {
        val untilChecked = PolicyErrorAction.DENY_UNTIL_CHECKED
        val missing = RemotePolicySettings.builder().source(directory.resolve("Absent.java").toUri().toString())
            .onError(untilChecked).build()
        assertThrows(PolicyDeniedException::class.java) { fixture(PlatformType.BUKKIT).start(missing) }

        val checkedOnce = policy("LaterPolicy", true).copy(onError = untilChecked)
        val later = fixture(PlatformType.BUKKIT)
        later.start(checkedOnce)
        later.ready.single().run()
        Files.delete(directory.resolve("LaterPolicy.java"))
        later.task!!.action.run(mock(TaskContext::class.java))
        assertEquals(0, later.closes)
        verify(later.platform, never()).disableOwner(later.owner)
    }

    @Test
    fun `unreadable policy and setup failures stop the library rather than ignore its policy`() {
        val missing = RemotePolicySettings.builder().source(directory.resolve("Missing.java").toUri().toString()).build()
        assertThrows(PolicyDeniedException::class.java) { fixture(PlatformType.VELOCITY).start(missing) }

        val unsupported = fixture(PlatformType.BUNGEECORD)
        val policy = policy("SetupPolicy", true)
        `when`(unsupported.platform.remotePolicyContext(unsupported.owner, policy.values)).thenThrow(UnsupportedOperationException("not supported"))
        assertThrows(UnsupportedOperationException::class.java) { unsupported.start(policy) }
        assertNull(unsupported.task)
    }

    @Test
    fun `shutdown prevents pending startup and already scheduled policy callbacks`() {
        val beforeReady = fixture(PlatformType.BUKKIT)
        beforeReady.start(policy("ClosedPolicy", true))
        beforeReady.closed = true
        beforeReady.ready.single().run()
        assertNull(beforeReady.task)

        val afterReady = fixture(PlatformType.VELOCITY)
        afterReady.start(policy("LatePolicy", false).copy(source = policy("LateAllow", true).source))
        afterReady.ready.single().run()
        afterReady.closed = true
        afterReady.task!!.action.run(mock(TaskContext::class.java))
        assertEquals(0, afterReady.closes)
        verify(afterReady.platform, never()).disableOwner(afterReady.owner)
    }

    @Test
    fun `invalid library declarations fail before bootstrap and old startup signatures remain`() {
        val platform = mock(PlatformAdapter::class.java)
        val modulePolicy = policy("ModulePolicy", true).copy(onDeny = DenyAction.DISABLE_MODULE)
        assertThrows(IllegalArgumentException::class.java) { PnLibraryRuntimeHost.start(Any(), platform, modulePolicy) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeRemotePolicy.validated(RemotePolicySettings("http://example.org/Unsafe.java")) }
        verifyNoInteractions(platform)
        assertNotNull(PnLibraryRuntimeHost::class.java.getMethod("start", Any::class.java, PlatformAdapter::class.java))
        assertNotNull(PnLibraryRuntimeHost::class.java.getMethod("startAndRegisterPlatform", Any::class.java, PlatformAdapter::class.java, Class::class.java,
            Any::class.java))
    }

    private fun policy(name: String, allowed: Boolean): RemotePolicySettings {
        val source = directory.resolve("$name.java")
        Files.write(source, """
            import org.pnfolder.pnlibrary.api.remote.*;
            public final class $name implements RemotePolicy {
                public RemotePolicyResult check(RemotePolicyContext context) {
                    if (!context.getProduct().getId().equals("pnlibrary")) throw new IllegalArgumentException("Wrong owner");
                    return ${if (allowed) "RemotePolicyResult.allow()" else "RemotePolicyResult.deny(\"Library version no longer supported\")"};
                }
            }
        """.trimIndent().toByteArray())
        return RemotePolicySettings.builder().source(source.toUri().toString()).checkEvery(Duration.ofMinutes(15))
            .value("product", "pnlibrary").build()
    }

    private fun fixture(type: PlatformType) = Fixture(type)

    private class Fixture(type: PlatformType) {
        val owner = Any()
        val library = mock(PnLibrary::class.java, withSettings().extraInterfaces(RuntimeResources::class.java))
        val platform = mock(PlatformAdapter::class.java)
        val tasks = mock(TaskScope::class.java)
        val ready = mutableListOf<Runnable>()
        var task: TaskSpec? = null
        var closed = false
        var closes = 0
        init {
            `when`(library.owner).thenReturn(owner)
            `when`(library.version).thenReturn("1.0.0")
            `when`((library as RuntimeResources).runtimeTasks).thenReturn(tasks)
            val logging = mock(LoggingService::class.java)
            `when`(library.logging).thenReturn(logging)
            `when`(logging.logger(owner, "remote-policy")).thenReturn(mock(PnLogger::class.java))
            `when`(platform.type).thenReturn(type)
            `when`(platform.implementationName).thenReturn(type.name)
            `when`(platform.ownerDetails(owner)).thenReturn(mapOf("name" to "pnLibrary", "authors" to "pnFolder"))
            doAnswer { ready += it.getArgument<Runnable>(0); null }.`when`(platform).whenServerReady(policyArgument(Runnable::class.java))
            doAnswer { it.getArgument<Runnable>(0).run(); null }.`when`(platform).executeGlobal(policyArgument(Runnable::class.java))
            doAnswer { task = it.getArgument(0); mock(TaskHandle::class.java) }.`when`(tasks).schedule(policyArgument(TaskSpec::class.java))
            val context = RemotePolicyContext.builder().product(ProductInfo("pnlibrary", "pnLibrary", "1.0.0"))
                .platform(PlatformInfo(type, type.name, "1.0"))
                .server(ServerInfo("1.0", MinecraftVersion.parseInfo(null))).build()
            `when`(platform.remotePolicyContext(owner, mapOf("product" to "pnlibrary"))).thenReturn(context)
        }
        fun start(policy: RemotePolicySettings) =
            RuntimeRemotePolicy.start(library, platform, RuntimeRemotePolicy.validated(policy), { closed }) { closes++; closed = true }
    }
}

// Mockito returns null while registering a matcher; an unconstrained generic avoids Kotlin's
// call-site non-null assertion without weakening the production API's parameter types.
private fun <T> policyArgument(type: Class<T>): T = any(type)
