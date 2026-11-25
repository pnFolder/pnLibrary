package org.pnfolder.pnlibrary.api.placeholders.route

/**
 * One part of the address.
 *
 * @property kind what the part is
 * @property text the text as it was written (`top`, `3`, `pad0`)
 * @property name the declared name: the word of a branch or field, the argument name of an argument, the name of a format
 * @property arguments what follows a colon in a format (`4` for `pad0:4`), otherwise empty
 */
data class RoutePart(val kind: RouteKind, val text: String, val name: String = text, val arguments: List<String> = emptyList())
