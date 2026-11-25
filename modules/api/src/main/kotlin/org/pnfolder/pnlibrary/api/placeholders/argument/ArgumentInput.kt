package org.pnfolder.pnlibrary.api.placeholders.argument

import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo

/**
 * Everything a converter knows when it turns one part of an address into a value.
 *
 * @property text what was written, such as `Wolves`
 * @property viewer who asks
 * @property route the whole asked address, every part with its kind
 * @property name the name of the parameter that receives the value
 * @property position the place of this argument among the arguments of the address, from 0
 * @property previous the arguments already read to the left of this one
 */
class ArgumentInput(
    val text: String,
    val viewer: ViewerInfo,
    val route: RouteInfo,
    val name: String,
    val position: Int,
    val previous: List<ArgumentValue<*>>,
) {
    /** Says why the text does not fit. The reason is shown by `/pn placeholders test` and kept in [ArgumentValue.error]. */
    fun fail(reason: String): Nothing = throw ArgumentRejected(reason)
}
