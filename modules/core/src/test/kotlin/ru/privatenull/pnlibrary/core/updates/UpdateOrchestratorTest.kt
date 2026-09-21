package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.update.ResolutionResult
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UpdateOrchestratorTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `coalesces checks announces changed plans and rejects stale staging`() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val announcements = mutableListOf<UpdatePlanSnapshot>()
        val staged = mutableListOf<UpdatePlanSnapshot>()
        var result: ResolutionResult = ResolutionResult.Ready(plan("2.0.0"))
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = {
                calls.incrementAndGet(); entered.countDown(); release.await(2, TimeUnit.SECONDS); result
            },
            stageAction = staged::add,
            announcement = announcements::add,
        )
        try {
            val first = orchestrator.checkNow()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val coalesced = orchestrator.checkNow()
            release.countDown()
            val old = first.toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(old.id, coalesced.toCompletableFuture().get().id)
            assertEquals(1, calls.get())
            assertEquals(1, announcements.size)

            result = ResolutionResult.Ready(plan("3.0.0"))
            val current = orchestrator.checkNow().toCompletableFuture().get()
            assertEquals(2, announcements.size)
            assertThrows(Exception::class.java) { orchestrator.stage(old.id).toCompletableFuture().get() }
            assertEquals(UpdateState.UPDATE_STAGED, orchestrator.stage(current.id).toCompletableFuture().get().state)
            assertEquals(1, staged.size)
        } finally { orchestrator.close() }
    }

    @Test
    fun `throttles identical announcements until repeat interval`() {
        val clock = MutableClock(Instant.EPOCH)
        val announcements = mutableListOf<UpdatePlanSnapshot>()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) },
            stageAction = {}, announcement = announcements::add, clock = clock,
        )
        try {
            orchestrator.checkNow().toCompletableFuture().get()
            orchestrator.checkNow().toCompletableFuture().get()
            assertEquals(1, announcements.size)
            clock.instant = clock.instant.plusSeconds(6 * 60 * 60)
            orchestrator.checkNow().toCompletableFuture().get()
            assertEquals(2, announcements.size)
        } finally { orchestrator.close() }
    }

    @Test
    fun `executor rejection completes check and stage futures instead of leaking`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) },
            stageAction = {}, announcement = {},
        )
        executor.shutdownNow()
        assertThrows(Exception::class.java) { orchestrator.checkNow().toCompletableFuture().get() }
        assertThrows(Exception::class.java) {
            orchestrator.stage(java.util.UUID.randomUUID()).toCompletableFuture().get()
        }
        assertDoesNotThrow { orchestrator.registrationsChanged() }
        orchestrator.close()
    }

    private fun plan(version: String): UpdatePlan {
        val id = ComponentId.of("pnlibrary")
        val semantic = SemanticVersion.parse(version)
        return UpdatePlan(1, listOf(ComponentChange(id, SemanticVersion.parse("1.0.0"), semantic)), listOf(
            ComponentRelease(id, semantic, UpdateChannel.STABLE, ApiVersionRange(1, 1), providesApi = 1),
        ))
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
