package org.pnfolder.pnlibrary.api.placeholders.tree

import org.pnfolder.pnlibrary.api.plugin.ModuleContext

/**
 * The placeholders of one module, written as a tree. Everything is registered where it is written; nothing has
 * to be registered or published separately.
 */
class GroupScope internal constructor(
    internal val data: GroupData,
) : BranchScope<Any, Unit>(data.roots, Any::class.java) {
    /** The format applied to every branch of the group that does not choose its own. */
    fun format(name: String) {
        data.format = FormatChoice.Named(name)
    }

    /** No default format for the group. */
    fun format(none: none) {
        data.format = FormatChoice.None
    }

    /** Who may read the placeholders of the group; a branch may narrow or widen it. */
    fun access(block: AccessScope.() -> Unit) {
        data.access = AccessScope().apply(block).rule ?: AccessRule.AllowAll
    }
}

/**
 * Registers the placeholders of this module.
 *
 * With [papi] set, every placeholder of the block is also shown to PlaceholderAPI as `%papi_module~path%`
 * (branches marked `inside()` are left out). Without it nothing is published outside.
 */
fun ModuleContext.placeholders(papi: String? = null, configure: GroupScope.() -> Unit) {
    val group = GroupData(papi?.also(::checkPrefix))
    GroupScope(group).configure()
    PlaceholderInstaller(placeholders, id.value).install(group)
}

private fun checkPrefix(prefix: String) {
    require(prefix.matches(Regex("[a-z0-9-]{2,32}"))) {
        "PlaceholderAPI prefix '$prefix' must be 2-32 characters of a-z, 0-9 and '-' (no '_': PlaceholderAPI splits on it)"
    }
}
