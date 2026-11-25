package org.pnfolder.pnlibrary.api.placeholders.route

import org.pnfolder.pnlibrary.api.placeholders.annotation.Route

/** The whole way of the asked address, handed to a parameter marked with [Route]. */
class RouteInfo(val parts: List<RoutePart>) {
    /** The address without formats: `clan.war.top.3.name`. */
    val path: String get() = parts.filter { it.kind != RouteKind.FORMAT }.joinToString(".") { it.text }

    /** The group, the first word. */
    val group: String get() = parts.firstOrNull()?.text.orEmpty()

    /** The field, the last word with the value. */
    val field: RoutePart? get() = parts.lastOrNull { it.kind == RouteKind.FIELD }

    /** The branches in the order written: `war`, `enemy`. */
    val branches: List<RoutePart> get() = of(RouteKind.BRANCH)

    /** The arguments written in the path, in order: the `3` of `top.3`. */
    val arguments: List<RoutePart> get() = of(RouteKind.ARGUMENT)

    /** The formats after the bars, in order: `pad0:4`, `upper`. */
    val formats: List<RoutePart> get() = of(RouteKind.FORMAT)

    /** Every part of [kind], in the order written. */
    fun of(kind: RouteKind): List<RoutePart> = parts.filter { it.kind == kind }

    /** The parts after the first part called [name]; empty when there is none. A branch `war` asks `after("war")`. */
    fun after(name: String): List<RoutePart> {
        val index = parts.indexOfFirst { it.kind != RouteKind.FORMAT && it.name == name }
        return if (index < 0) emptyList() else parts.drop(index + 1)
    }

    /** Whether the address has a format called [name]. */
    fun hasFormat(name: String): Boolean = parts.any { it.kind == RouteKind.FORMAT && it.name == name }

    override fun toString(): String = parts.joinToString(".") { "${it.kind.name.lowercase()}:${it.text}" }
}
