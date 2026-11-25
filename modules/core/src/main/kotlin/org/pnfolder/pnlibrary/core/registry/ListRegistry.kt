package org.pnfolder.pnlibrary.core.registry

import org.pnfolder.pnlibrary.api.registry.Registry
import org.pnfolder.pnlibrary.api.registry.RegistryEntry

/** A [Registry] over a list that is asked again on every call, so it always shows what is registered now. */
internal class ListRegistry<T : Any>(
    override val name: String,
    private val source: () -> List<RegistryEntry<T>>,
) : Registry<T> {
    override fun entries(): List<RegistryEntry<T>> = source()
}
