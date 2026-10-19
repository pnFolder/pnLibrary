package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.AsyncPlaceholderResolver
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderBuilder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderUpdater
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/** Collects a placeholder definition and installs it as one immutable registration. */
internal class PlaceholderDefinitionBuilder<T : Any>(
    private val owner: PluginId,
    private val key: PlaceholderKey<T>,
    private val placeholderApiEnabled: Boolean,
    private val adapterLookup: (String) -> PlaceholderAdapter?,
    private val install: (PlaceholderEntry<T>) -> Unit,
) : PlaceholderBuilder<T> {
    private var resolver: ((PlaceholderRequest) -> CompletionStage<T?>)? = null
    private var updater: PlaceholderUpdater<T>? = null
    private var access = PlaceholderAccess.ownerOnly()
    private var updateAccess = PlaceholderAccess.ownerOnly()
    private var cachePolicy = PlaceholderCachePolicy.none()
    private var fallback: T? = null
    private val publications = mutableListOf<PlaceholderPublication>()

    override fun resolve(resolver: PlaceholderResolver<T>) = apply {
        this.resolver = { request -> CompletableFuture.completedFuture(resolver.resolve(request)) }
    }

    override fun resolveAsync(resolver: AsyncPlaceholderResolver<T>) = apply {
        this.resolver = resolver::resolve
    }

    override fun update(updater: PlaceholderUpdater<T>) = apply { this.updater = updater }
    override fun updateAccess(access: PlaceholderAccess) = apply { this.updateAccess = access }
    override fun access(access: PlaceholderAccess) = apply { this.access = access }

    override fun access(configure: Consumer<PlaceholderAccess.Builder>) = apply {
        access = PlaceholderAccess.builder().also(configure::accept).build()
    }

    override fun cache(policy: PlaceholderCachePolicy) = apply { cachePolicy = policy }
    override fun fallback(value: T) = apply { fallback = value }
    override fun publish(publication: PlaceholderPublication) = apply { publications += publication }

    fun install(): PlaceholderRegistration<T> {
        val entry = PlaceholderEntry(
            owner = owner,
            key = key,
            resolver = resolver ?: error("Placeholder ${key.value} has no resolver"),
            updater = updater,
            access = access,
            updateAccess = updateAccess,
            cachePolicy = cachePolicy,
            fallback = fallback,
            adapterLookup = adapterLookup,
        )
        install(entry)
        publications
            .filter { publication ->
                publication.adapterId.lowercase(Locale.ROOT) != "placeholderapi" || placeholderApiEnabled
            }
            .forEach(entry::publish)
        return entry
    }
}
