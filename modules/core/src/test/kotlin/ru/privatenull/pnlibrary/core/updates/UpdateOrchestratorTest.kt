package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.ResolutionResult
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
    fun `a check returning after shutdown cannot announce or automatically stage its result`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val observed = AtomicInteger()
        val staged = AtomicInteger()
        val announced = AtomicInteger()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(downloads = UpdateConfiguration.Downloads(automatic = true)),
            UpdateStateStore(directory), executor,
            resolver = {
                entered.countDown()
                while (release.count > 0) try { release.await() } catch (_: InterruptedException) { }
                ResolutionResult.Ready(plan("2.0.0"))
            },
            stageAction = { staged.incrementAndGet() }, announcement = { announced.incrementAndGet() },
            observeState = { _, _, _ -> observed.incrementAndGet() },
        )
        try {
            val result = orchestrator.checkNow().toCompletableFuture()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            orchestrator.close()
            release.countDown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            assertTrue(result.isCompletedExceptionally)
            assertEquals(0, observed.get())
            assertEquals(0, staged.get())
            assertEquals(0, announced.get())
        } finally { release.countDown(); orchestrator.close() }
    }

    @Test
    fun `close completes queued operations without executing their mutations`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            try { release.await() } catch (_: InterruptedException) { }
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val mutations = AtomicInteger()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) },
            stageAction = { mutations.incrementAndGet() }, announcement = {},
        )
        try {
            val check = orchestrator.checkNow().toCompletableFuture()
            val stage = orchestrator.stage(java.util.UUID.randomUUID()).toCompletableFuture()
            val rollback = orchestrator.rollbackTo(UpdateActor.SYSTEM) {
                mutations.incrementAndGet()
                error("must not execute")
            }.toCompletableFuture()
            orchestrator.close()
            listOf(check, stage, rollback).forEach { future ->
                assertThrows(java.util.concurrent.ExecutionException::class.java) {
                    future.get(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            assertEquals(0, mutations.get())
            assertTrue(orchestrator.stage(java.util.UUID.randomUUID()).toCompletableFuture().isCompletedExceptionally)
        } finally { release.countDown(); orchestrator.close() }
    }

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
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (orchestrator.history() as MutableList<UpdatePlanSnapshot>).clear()
            }
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

    @Test
    fun `manual rollback runs action and publishes rolled back state`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        var rollbackRuns = 0
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) },
            stageAction = {}, announcement = {},
        )
        try {
            val available = orchestrator.checkNow().toCompletableFuture().get()

            val restored = orchestrator.rollback { rollbackRuns++ }.toCompletableFuture().get()

            assertEquals(1, rollbackRuns)
            assertEquals(UpdateState.ROLLED_BACK, restored.state)
            assertEquals(available.id, restored.id)
            assertEquals(UpdateState.ROLLED_BACK, orchestrator.currentPlan().orElseThrow().state)
        } finally {
            orchestrator.close()
        }
    }

    @Test
    fun `forced manual check waits for cached check then resolves remotely`() {
        val cachedEntered = CountDownLatch(1)
        val cachedRelease = CountDownLatch(1)
        val remoteCalls = AtomicInteger()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = {
                cachedEntered.countDown()
                cachedRelease.await(2, TimeUnit.SECONDS)
                ResolutionResult.Ready(plan("2.0.0"))
            },
            remoteResolver = {
                remoteCalls.incrementAndGet()
                ResolutionResult.Ready(plan("3.0.0"))
            },
            stageAction = {}, announcement = {},
        )
        try {
            val cached = orchestrator.checkNow(false)
            assertTrue(cachedEntered.await(1, TimeUnit.SECONDS))
            val forced = orchestrator.checkNow(true)
            cachedRelease.countDown()

            assertEquals("2.0.0", cached.toCompletableFuture().get(2, TimeUnit.SECONDS).plan?.changes?.single()?.to.toString())
            assertEquals("3.0.0", forced.toCompletableFuture().get(2, TimeUnit.SECONDS).plan?.changes?.single()?.to.toString())
            assertEquals(1, remoteCalls.get())
        } finally {
            orchestrator.close()
        }
    }

    @Test
    fun `failed remote check publishes durable failed snapshot with cause`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { error("GitHub вернул HTTP 404") },
            stageAction = {}, announcement = {},
        )
        try {
            val failed = orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS)

            assertEquals(UpdateState.FAILED, failed.state)
            assertEquals("GitHub вернул HTTP 404", failed.message)
            assertEquals(UpdateState.FAILED, UpdateStateStore(directory).current()?.state)
        } finally {
            orchestrator.close()
        }
    }

    @Test
    fun `manual operations retain their initiator through the executor`() {
        val actor = ru.privatenull.pnlibrary.api.updates.UpdateActor(
            ru.privatenull.pnlibrary.api.updates.UpdateActor.Kind.CONSOLE, "console", "Console",
        )
        val observations = mutableListOf<Pair<String, ru.privatenull.pnlibrary.api.updates.UpdateActor>>()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) }, stageAction = {}, announcement = {},
            observeState = { _, recordedActor, operation -> observations.add(operation to recordedActor) },
        )
        try {
            val plan = orchestrator.checkNow(actor = actor).toCompletableFuture().get(2, TimeUnit.SECONDS)
            orchestrator.stage(plan.id, actor).toCompletableFuture().get(2, TimeUnit.SECONDS)
            orchestrator.rollback(actor) {}.toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(listOf("CHECK" to actor, "STAGE" to actor, "ROLLBACK" to actor), observations)
        } finally { orchestrator.close() }
    }

    @Test
    fun `automatic staging is attributed to the system rather than the person checking`() {
        val actor = ru.privatenull.pnlibrary.api.updates.UpdateActor(
            ru.privatenull.pnlibrary.api.updates.UpdateActor.Kind.CONSOLE, "console", "Console",
        )
        val observations = mutableListOf<Pair<String, ru.privatenull.pnlibrary.api.updates.UpdateActor>>()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(downloads = UpdateConfiguration.Downloads(automatic = true)),
            UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) }, stageAction = {}, announcement = {},
            observeState = { _, recordedActor, operation -> observations.add(operation to recordedActor) },
        )
        try {
            orchestrator.checkNow(actor = actor).toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(listOf("CHECK" to actor, "STAGE" to ru.privatenull.pnlibrary.api.updates.UpdateActor.SYSTEM), observations)
        } finally { orchestrator.close() }
    }

    @Test
    fun `failed manual staging records its initiator without replacing the available plan`() {
        val actor = ru.privatenull.pnlibrary.api.updates.UpdateActor(
            ru.privatenull.pnlibrary.api.updates.UpdateActor.Kind.CONSOLE, "console", "Console",
        )
        val observations = mutableListOf<UpdatePlanSnapshot>()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("2.0.0")) },
            stageAction = { error("checksum mismatch") }, announcement = {},
            observeState = { snapshot, recordedActor, operation ->
                if (operation == "STAGE") {
                    assertEquals(actor, recordedActor)
                    observations.add(snapshot)
                }
            },
        )
        try {
            val selected = orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertThrows(Exception::class.java) { orchestrator.stage(selected.id, actor).toCompletableFuture().get(2, TimeUnit.SECONDS) }
            assertEquals(UpdateState.FAILED, observations.single().state)
            assertEquals("checksum mismatch", observations.single().message)
            assertEquals(UpdateState.UPDATE_AVAILABLE, orchestrator.currentPlan().get().state)
        } finally { orchestrator.close() }
    }

    @Test
    fun `saved rollback can publish its own plan without an unrelated current plan`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val orchestrator = UpdateOrchestrator(
            UpdateConfiguration(), UpdateStateStore(directory), executor,
            resolver = { ResolutionResult.Ready(plan("3.0.0")) }, stageAction = {}, announcement = {},
        )
        val id = java.util.UUID.randomUUID()
        try {
            val saved = UpdatePlanSnapshot(id, 0, UpdateState.ROLLED_BACK, plan("2.0.0"), emptyList(), "saved set")
            val restored = orchestrator.rollbackTo(ru.privatenull.pnlibrary.api.updates.UpdateActor.SYSTEM) { saved }
                .toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(id, restored.id)
            assertEquals("2.0.0", restored.plan?.changes?.single()?.to.toString())
            assertEquals("saved set", restored.message)
            assertEquals(UpdateState.ROLLED_BACK, orchestrator.currentPlan().get().state)
        } finally { orchestrator.close() }
    }

    private fun plan(version: String): UpdatePlan {
        val id = ProductId.of("pnlibrary")
        val semantic = SemanticVersion.parse(version)
        return UpdatePlan(1, listOf(ProductChange(id, SemanticVersion.parse("1.0.0"), semantic)), listOf(
            ProductRelease(id, semantic, UpdateChannel.STABLE, ApiVersionRange(1, 1), providesApi = 1),
        ))
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
