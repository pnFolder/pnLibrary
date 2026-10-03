package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapterState
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderPublication
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderResolver
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Maintains one placeholder publication while its external adapter comes and goes. */
internal class PlaceholderPublicationBinding(
    private val publication: PlaceholderPublication,
    private val owner: PluginId,
    private val key: String,
    private val resolver: PlaceholderResolver<Any>,
) : ExternalPlaceholderRegistration {
    val adapterId: String = publication.adapterId.lowercase(Locale.ROOT)

    private val closed = AtomicBoolean(false)

    @Volatile
    private var delegate: ExternalPlaceholderRegistration? = null

    @Volatile
    private var publicationFailed = false

    override val state: PlaceholderAdapterState
        get() = when {
            closed.get() -> PlaceholderAdapterState.CLOSED
            publicationFailed -> PlaceholderAdapterState.FAILED
            delegate == null -> PlaceholderAdapterState.UNAVAILABLE
            else -> delegate!!.state
        }

    @Synchronized
    fun attach(adapter: PlaceholderAdapter) {
        if (closed.get() || adapter.id.lowercase(Locale.ROOT) != adapterId) return

        closeDelegate()
        publicationFailed = false
        runCatching { adapter.publish(owner, key, resolver, publication) }
            .onSuccess { delegate = it }
            .onFailure { publicationFailed = true }
    }

    @Synchronized
    fun detach() {
        closeDelegate()
        publicationFailed = false
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) detach()
    }

    private fun closeDelegate() {
        delegate?.let { registration -> runCatching(registration::close) }
        delegate = null
    }
}
