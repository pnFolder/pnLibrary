package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class RemoteExecutorTest {
    private class Call(val product: String, val enabled: Boolean, val actor: UpdateActor)

    private val calls = mutableListOf<Call>()
    private var automaticAnswer: CompletionStage<UpdateSnapshot> = CompletableFuture.completedFuture(null)
    private val updates: UpdateService = mock(UpdateService::class.java) { call ->
        when (call.method.name) {
            "setAutomaticDownload" -> {
                calls += Call(call.getArgument(0), call.getArgument(1), call.getArgument(2))
                automaticAnswer
            }
            else -> null
        }
    }
    private val library = mock(PnLibrary::class.java).also { `when`(it.updates).thenReturn(updates) }
    private var now = Instant.parse("2026-11-04T10:00:00Z")
    private val executor = RemoteExecutor(library) { now }
    private val results = mutableListOf<RemoteResult>()

    private fun request(id: UUID = UUID.randomUUID(), action: RemoteAction = RemoteAction.AUTO_ON, product: String? = "shop") =
        RemoteRequest(id, "lobby", "proxy", "Admin", action, product, now)

    private fun run(request: RemoteRequest) = executor.run(request) { results += it }

    @Test
    fun `automatic download is switched through the update service for a remote actor`() {
        run(request(action = RemoteAction.AUTO_ON))

        val call = calls.single()
        assertEquals("shop", call.product)
        assertTrue(call.enabled)
        assertEquals(UpdateActor.Kind.INTEGRATION, call.actor.kind)
        assertEquals("remote:proxy", call.actor.id)
        assertTrue(results.single().ok)
        assertTrue(results.single().text.contains("включена"))
    }

    @Test
    fun `a failure of the update service becomes a refusal with its reason`() {
        automaticAnswer = CompletableFuture<UpdateSnapshot>().also { it.completeExceptionally(IllegalStateException("диск полон")) }

        run(request(action = RemoteAction.AUTO_OFF))

        assertFalse(results.single().ok)
        assertTrue(results.single().text.contains("диск полон"))
    }

    @Test
    fun `a request that was already carried out is refused`() {
        val same = request()

        run(same)
        run(same)

        assertEquals(listOf(true, false), results.map { it.ok })
        assertTrue(results.last().text.contains("уже выполнялся"))
        assertEquals(1, calls.size)
    }

    @Test
    fun `an old request id is forgotten after ten minutes`() {
        val same = request()

        run(same)
        now = now.plus(Duration.ofMinutes(11))
        run(same)

        assertEquals(listOf(true, true), results.map { it.ok })
    }

    @Test
    fun `updating a plugin the library does not know is refused`() {
        run(request(action = RemoteAction.UPDATE))

        assertFalse(results.single().ok)
        assertTrue(results.single().text.contains("не подключён"))
    }
}
