package org.pnfolder.pnlibrary.api.placeholders.route

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue

/** What a part of an address is. For `{clan.war.top.3.name|pad0:4}`: group, branch, branch, argument, field, format. */
enum class RouteKind {
    /** The first word, the one of `@Placeholders("clan")`. */
    GROUP,

    /** A word that continues to other words: a method or property that returns an object. */
    BRANCH,

    /** A word that fills an [ArgumentValue] parameter: the `3` above. */
    ARGUMENT,

    /** The last word, the one that has the value. */
    FIELD,

    /** A part after a bar: `pad0:4`. */
    FORMAT,
}
