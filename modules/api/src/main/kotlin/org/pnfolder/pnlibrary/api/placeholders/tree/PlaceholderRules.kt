package org.pnfolder.pnlibrary.api.placeholders.tree

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess

/** Who may read (or change) a placeholder. A denial always beats an allowance. */
sealed class AccessRule {
    internal abstract fun toAccess(): PlaceholderAccess

    internal object AllowAll : AccessRule() {
        override fun toAccess(): PlaceholderAccess = PlaceholderAccess.shared()
    }

    internal object DenyAll : AccessRule() {
        override fun toAccess(): PlaceholderAccess = PlaceholderAccess.ownerOnly()
    }

    internal class AllowOnly(private val ids: List<String>) : AccessRule() {
        override fun toAccess(): PlaceholderAccess = PlaceholderAccess.builder().owner().allow(*ids.toTypedArray()).build()
    }

    internal class Deny(private val ids: List<String>) : AccessRule() {
        override fun toAccess(): PlaceholderAccess =
            PlaceholderAccess.builder().owner().allowAllLibraryPlugins().deny(*ids.toTypedArray()).build()
    }
}

/** Every plugin of the library may read. This is the default. */
fun allowAll(): AccessRule = AccessRule.AllowAll

/** Only the owning plugin may read. */
fun denyAll(): AccessRule = AccessRule.DenyAll

/** The owner and the listed plugins may read. */
fun allowOnly(vararg plugins: String): AccessRule = AccessRule.AllowOnly(plugins.toList())

/** Every plugin may read except the listed ones. */
fun deny(vararg plugins: String): AccessRule = AccessRule.Deny(plugins.toList())

/** Picks the access rule inside `access { ... }`. */
class AccessScope internal constructor() {
    internal var rule: AccessRule? = null

    /** Every plugin may read. */
    fun allowAll() { rule = AccessRule.AllowAll }

    /** Only the owner may read. */
    fun denyAll() { rule = AccessRule.DenyAll }

    /** The owner and the listed plugins may read. */
    fun allowOnly(vararg plugins: String) { rule = AccessRule.AllowOnly(plugins.toList()) }

    /** The owner and every plugin except the listed ones may read. */
    fun deny(vararg plugins: String) { rule = AccessRule.Deny(plugins.toList()) }
}

/** The default format of a branch: no format at all. */
object none

/** How a branch picks its default format. */
internal sealed class FormatChoice {
    object Inherit : FormatChoice()
    object None : FormatChoice()
    class Named(val name: String) : FormatChoice()

    fun resolve(inherited: String?): String? = when (this) {
        Inherit -> inherited
        None -> null
        is Named -> name
    }
}
