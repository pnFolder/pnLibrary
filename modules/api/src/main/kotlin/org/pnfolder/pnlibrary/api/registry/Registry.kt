package org.pnfolder.pnlibrary.api.registry

import org.jetbrains.annotations.ApiStatus

/**
 * A read-only list of everything of one kind that plugins registered: placeholders, actions, formats. Every kind gives
 * it the same way, so one list, one search and one menu work for all of them.
 *
 * ```kotlin
 * val placeholders = library.placeholders.registry()
 * placeholders.entries().filter { it.owner.startsWith("shop/") }
 * placeholders.require("shop.coins")
 * ```
 */
@ApiStatus.NonExtendable
interface Registry<T : Any> {
    /** What this registry holds, for messages: `placeholders`. */
    val name: String

    /** Everything registered right now, with owner and visibility. */
    fun entries(): List<RegistryEntry<T>>

    /** The thing named [key], or `null` when there is none. */
    operator fun get(key: String): T? = entries().firstOrNull { it.key == key }?.value

    /** The thing named [key]; throws when there is none. */
    fun require(key: String): T = get(key) ?: throw NoSuchElementException("No '$key' in $name")

    /** Whether something is registered under [key]. */
    operator fun contains(key: String): Boolean = entries().any { it.key == key }
}
