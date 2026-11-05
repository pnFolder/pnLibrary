package org.pnfolder.pnlibrary.api.placeholders.tree

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderFormatter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.ModuleContext

/** The formats a module adds; each is used in text as `{key|name}` or `{key|name:argument,argument}`. */
class FormatScope @PublishedApi internal constructor(
    @PublishedApi internal val service: PlaceholderService,
) {
    /** A format for values of type [T]: `format<Int>("rank") { "#$it" }`. */
    inline fun <reified T : Any> format(name: String, noinline block: (T) -> Any?) {
        service.formatter(name, T::class.javaObjectType, PlaceholderFormatter { value, _, _ -> block(value).toString() })
    }

    /** A format with arguments after the colon: `format<Int>("pad0") { n, args -> ... }` is `{key|pad0:5}`. */
    inline fun <reified T : Any> format(name: String, noinline block: (T, List<String>) -> Any?) {
        service.formatter(name, T::class.javaObjectType, PlaceholderFormatter { value, args, _ -> block(value, args).toString() })
    }
}

/** Registers formats of this module. They are released with the module and apply to every placeholder it owns. */
fun ModuleContext.formats(configure: FormatScope.() -> Unit) {
    FormatScope(placeholders).configure()
}
