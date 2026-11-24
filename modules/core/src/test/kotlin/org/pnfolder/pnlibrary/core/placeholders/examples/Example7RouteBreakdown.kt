package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Format
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Route
import org.pnfolder.pnlibrary.api.placeholders.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.RouteKind
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo

// Example 7: one address taken apart.
//
//   {clan.war.top.3.name|pad0:4|upper}
//
//    clan   .war     .top     .3        .name   |pad0:4        |upper
//    GROUP   BRANCH   BRANCH   ARGUMENT  FIELD   FORMAT         FORMAT
//                                                 arguments=[4]  arguments=[]
//
// The method `top` below receives this route and reads every part of it.

@Placeholders("clan")
internal class ClanWarTree {

    /** The branch `war`: the first method on the way. Its route is the whole address, not only the part up to `war`. */
    @Placeholder("War of the clan")
    fun war(@Viewer viewer: ViewerInfo): Reply<WarTop> {
        Clans.of(viewer.playerId) ?: return Reply.stop("no clan")
        return Reply.next(WarTop())
    }
}

internal class WarTop {

    /** The branch `top` with an argument: `place` is the `3`. */
    @Placeholder("Place in the war top")
    fun top(@Route route: RouteInfo, @Argument(range = "1..10") place: ArgumentValue<Int>): Reply<TopPlace> {
        // Everything the route knows, one line each.
        val group: String = route.group // "clan"
        val branches: List<String> = route.branches.map { it.text } // ["war", "top"]
        val argument: String = route.arguments.single().text // "3", as the player wrote it
        val argumentName: String = route.arguments.single().name // "place", the parameter name
        val field: String? = route.field?.text // "name"
        val formats: List<String> = route.formats.map { it.name } // ["pad0", "upper"]
        val padWidth: String? = route.formats.firstOrNull { it.name == "pad0" }?.arguments?.firstOrNull() // "4"
        val path: String = route.path // "clan.war.top.3.name"
        val belowWar: List<String> = route.after("war").map { it.text } // ["top", "3", "name"]
        val parts: List<String> = route.parts.map { "${it.kind}:${it.text}" }
        // GROUP:clan BRANCH:war BRANCH:top ARGUMENT:3 FIELD:name FORMAT:pad0 FORMAT:upper
        val isField: Boolean = route.of(RouteKind.FIELD).size == 1 // true
        val loud: Boolean = route.hasFormat("upper") // true

        // Ending early with an answer that suits the asked field and format.
        if (place.required() > 5) {
            return Reply.stop(if (field == "kills" || route.hasFormat("pad0")) 0 else "nobody")
        }
        val seen = "$group/${branches.size}/$argument/$argumentName/$formats/$padWidth/$path/$belowWar/$parts/$isField/$loud"
        return Reply.next(TopPlace(place.required(), seen))
    }
}

internal class TopPlace(private val place: Int, private val debug: String) {
    @Placeholder val name: Reply<String> get() = Reply.of("player$place")

    @Placeholder val kills: Reply<Int> get() = Reply.of(100 - place)

    /** `{clan.war.top.3.debug}` shows what the route looked like. */
    @Placeholder val debug2: Reply<String> get() = Reply.of(debug)
}

/** The formats of the address: `pad0:4` and `upper`. They are not in the route of the field, they are applied after it. */
internal class WarFormats {
    @Format("pad0")
    fun pad0(n: Int, @Argument width: ArgumentValue<Int>): String = n.toString().padStart(width.required(), '0')

    @Format("upper")
    fun upper(text: String): String = text.uppercase()
}

// Registration of the whole example:
//   context.placeholders.formats(WarFormats())
//   context.placeholders.register(ClanWarTree())
// Asking:
//   context.placeholders.render("{clan.war.top.3.name|upper}", player)      // PLAYER3
//   context.placeholders.render("{clan.war.top.3.kills|pad0:4}", player)    // 0097
//   context.placeholders.render("{clan.war.top.99.name}", player)           // the place is outside 1..10: the address stays
