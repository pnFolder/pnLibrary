package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterCapabilities
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.plugin.PluginId

class PlaceholderPublicationBindingTest {
    @Test
    fun `binding follows adapter attachment lifecycle`() {
        val publication = PlaceholderPublication("bridge")
        val binding = PlaceholderPublicationBinding(
            publication = publication,
            owner = PluginId.of("demo"),
            key = "status",
            resolver = PlaceholderResolver { "online" },
        )
        val adapter = RecordingAdapter("bridge")

        assertEquals(PlaceholderAdapterState.UNAVAILABLE, binding.state)

        binding.attach(adapter)

        assertEquals(PlaceholderAdapterState.REGISTERED, binding.state)
        assertEquals(1, adapter.publications)

        binding.detach()

        assertEquals(PlaceholderAdapterState.UNAVAILABLE, binding.state)
        assertTrue(adapter.lastRegistration!!.closed)

        binding.close()

        assertTrue(binding.isClosed)
        assertEquals(PlaceholderAdapterState.CLOSED, binding.state)

        binding.attach(adapter)
        assertEquals(1, adapter.publications)
    }

    private class RecordingAdapter(override val id: String) : PlaceholderAdapter {
        override val state = PlaceholderAdapterState.AVAILABLE
        override val capabilities = PlaceholderAdapterCapabilities()
        var publications = 0
        var lastRegistration: RecordingRegistration? = null

        override fun publish(
            owner: PluginId,
            key: String,
            resolver: PlaceholderResolver<Any>,
            publication: PlaceholderPublication,
        ): ExternalPlaceholderRegistration {
            publications++
            return RecordingRegistration().also { lastRegistration = it }
        }
    }

    private class RecordingRegistration : ExternalPlaceholderRegistration {
        var closed = false
        override val state: PlaceholderAdapterState
            get() = if (closed) PlaceholderAdapterState.CLOSED else PlaceholderAdapterState.REGISTERED

        override fun close() {
            closed = true
        }
    }
}
