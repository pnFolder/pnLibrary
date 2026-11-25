@file:JvmName("AudienceExtensions")

package org.pnfolder.pnlibrary.api.audienceB2

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.permission.PermissionChecker
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audienceB2.delivery.Delivery
import java.util.Locale
import java.util.UUID
import java.util.function.Supplier

/*
 * Who an Audience is, read from the Adventure pointers every platform fills in.
 *
 *     if (who.hasPermission("shop.use")) who.sendMessage(Component.text("Welcome, ${who.name}"))
 */

/** The name, or `Unknown` when the audience is not one sender. */
val Audience.name: String get() = get(Identity.NAME).orElse("Unknown")

/** The player's UUID, or `null` for the console and for groups. */
val Audience.uniqueId: UUID? get() = get(Identity.UUID).orElse(null)

/** The name shown to others, the nickname where the platform has one; falls back to [name]. */
val Audience.displayName: Component get() = get(Identity.DISPLAY_NAME).orElseGet { Component.text(name) }

/** The client's language, or `null` when the platform does not tell. */
val Audience.locale: Locale? get() = get(Identity.LOCALE).orElse(null)

/** Whether the platform grants [permission]; `false` when it cannot tell. */
fun Audience.hasPermission(permission: String): Boolean =
    get(PermissionChecker.POINTER).map { it.test(permission) }.orElse(false)

/** An audience of whoever [receivers] returns now, asked again on every operation. */
fun dynamicAudience(receivers: Supplier<out Iterable<Audience>>): Audience = object : ForwardingAudience {
    override fun audiences(): Iterable<Audience> = receivers.get()
}

/** Runs [operation] on this audience and reports it: `audience.deliver { it.sendMessage(text) }`. */
fun Audience.deliver(operation: (Audience) -> Unit): Delivery = Delivery.to(this, operation)
