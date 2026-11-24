@file:JvmName("Senders")

package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.permission.PermissionChecker
import net.kyori.adventure.pointer.Pointers
import net.kyori.adventure.text.Component
import net.kyori.adventure.util.TriState
import java.util.Locale
import java.util.UUID
import java.util.function.Supplier

/*
 * Who an Audience is, read from the Adventure pointers every platform fills in. The console has a name but no UUID.
 *
 *     fun greet(who: Audience) {
 *         if (who.hasPermission("shop.use")) who.sendMessage(Component.text("Welcome, ${who.name}"))
 *     }
 */

/** Current human-readable name, or `Unknown` when the audience is not one sender. */
val Audience.name: String get() = get(Identity.NAME).orElse("Unknown")

/** Whether this is a connected player. */
val Audience.isPlayer: Boolean get() = get(Identity.UUID).isPresent

/** Whether this is the server console: a sender that is not a player. */
val Audience.isConsole: Boolean get() = !isPlayer

/** Stable platform-independent identifier: the player UUID as text, or the name for the console. */
val Audience.id: String get() = get(Identity.UUID).map(UUID::toString).orElseGet { name }

/** Whether the platform grants this audience [permission]; `false` when it cannot tell. */
fun Audience.hasPermission(permission: String): Boolean = get(PermissionChecker.POINTER).map { it.test(permission) }.orElse(false)

/** Name shown to others, the nickname on platforms that have one; falls back to [name]. */
val Audience.displayName: Component get() = get(Identity.DISPLAY_NAME).orElseGet { Component.text(name) }

/** Language of the client, or `null` when the platform does not tell. */
val Audience.locale: Locale? get() = get(Identity.LOCALE).orElse(null)

/**
 * Pointers for a sender: its [name], its [uniqueId] when it is a player, and a permission check.
 * Platform modules give them back from `pointers()`.
 */
fun senderPointers(name: String, uniqueId: UUID? = null, permission: (String) -> Boolean): Pointers =
    senderPointers({ name }, uniqueId, permission)

/** The same with a [name] that is read when asked for, for senders whose name can change. */
fun senderPointers(name: () -> String, uniqueId: UUID? = null, permission: (String) -> Boolean): Pointers =
    pointers(name, uniqueId, permission).build()

/**
 * Pointers for a player: everything [senderPointers] gives, plus [displayName] (the nickname) and the client's
 * [locale]. Both are read when asked for; return `null` when the platform cannot tell.
 */
fun playerPointers(
    name: () -> String,
    uniqueId: UUID,
    displayName: () -> Component?,
    locale: () -> Locale?,
    permission: (String) -> Boolean,
): Pointers = pointers(name, uniqueId, permission).apply {
    withDynamic(Identity.DISPLAY_NAME, displayName)
    withDynamic(Identity.LOCALE, locale)
}.build()

private fun pointers(name: () -> String, uniqueId: UUID?, permission: (String) -> Boolean): Pointers.Builder =
    Pointers.builder().apply {
        withDynamic(Identity.NAME, name)
        if (uniqueId != null) withStatic(Identity.UUID, uniqueId)
        withStatic(PermissionChecker.POINTER, PermissionChecker { TriState.byBoolean(permission(it)) })
    }

/**
 * An audience of whoever [receivers] returns now, asked again for every operation: "all online players" whose
 * membership changes between two actions.
 */
fun dynamicAudience(receivers: Supplier<out Iterable<Audience>>): Audience = object : net.kyori.adventure.audience.ForwardingAudience {
    override fun audiences(): Iterable<Audience> = receivers.get()
}
