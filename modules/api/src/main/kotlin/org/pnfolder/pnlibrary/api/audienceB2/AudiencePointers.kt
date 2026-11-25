package org.pnfolder.pnlibrary.api.audienceB2

import net.kyori.adventure.identity.Identity
import net.kyori.adventure.permission.PermissionChecker
import net.kyori.adventure.pointer.Pointers
import net.kyori.adventure.text.Component
import net.kyori.adventure.util.TriState
import java.util.Locale
import java.util.UUID

/**
 * Builds the Adventure [Pointers] a platform returns from `pointers()`. Values given as functions are read when
 * asked for, so a renamed player shows the new name.
 *
 * ```kotlin
 * override fun pointers() = AudiencePointers.builder()
 *     .name { handle.name }
 *     .uniqueId(handle.uniqueId)
 *     .permission { handle.hasPermission(it) }
 *     .build()
 * ```
 */
class AudiencePointers private constructor() {
    /** Collects the pointers; only the name and the permission check are required. */
    class Builder internal constructor() {
        private var name: (() -> String)? = null
        private var permission: ((String) -> Boolean)? = null
        private val pointers = Pointers.builder()

        /** The name, read when asked for. */
        fun name(name: () -> String): Builder = apply { this.name = name }

        /** The UUID; leave it out for the console. */
        fun uniqueId(uniqueId: UUID): Builder = apply { pointers.withStatic(Identity.UUID, uniqueId) }

        /** The nickname shown to others, read when asked for; `null` falls back to the name. */
        fun displayName(displayName: () -> Component?): Builder =
            apply { pointers.withDynamic(Identity.DISPLAY_NAME, displayName) }

        /** The client's language, read when asked for; `null` when the platform cannot tell. */
        fun locale(locale: () -> Locale?): Builder = apply { pointers.withDynamic(Identity.LOCALE, locale) }

        /** Whether the sender holds a permission. */
        fun permission(permission: (String) -> Boolean): Builder = apply { this.permission = permission }

        /** Builds the pointers. */
        fun build(): Pointers {
            val name = requireNotNull(name) { "name is required" }
            val permission = requireNotNull(permission) { "permission is required" }
            return pointers
                .withDynamic(Identity.NAME, name)
                .withStatic(PermissionChecker.POINTER, PermissionChecker { TriState.byBoolean(permission(it)) })
                .build()
        }
    }

    /** Ways to start. */
    companion object {
        /** A new builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
