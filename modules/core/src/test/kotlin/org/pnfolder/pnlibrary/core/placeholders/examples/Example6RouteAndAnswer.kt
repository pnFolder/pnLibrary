package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Invalid
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Route
import org.pnfolder.pnlibrary.api.placeholders.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.RouteKind
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo

// Example 6: the way of the address, and ending it early.
//
// For `{duel.war.enemy.leader.kills|pad0:4}` the parts are:
//   duel = GROUP, war = BRANCH, enemy = BRANCH, leader = BRANCH, kills = FIELD, pad0 = FORMAT (arguments ["4"]).
// An argument in the path (`{duel.top.3.name}`, the 3) is an ARGUMENT part.

@Placeholders("duel")
internal class DuelValues {

    /**
     * `Reply.stop(...)` ends the whole address with that value: nothing below `war` is called.
     * The route tells which field was asked, so the answer can differ: a number for `score`, a text for the rest.
     */
    @Placeholder("The war of the clan")
    fun war(@Viewer viewer: ViewerInfo, @Route route: RouteInfo): Reply<DuelWar> {
        val clan = Clans.of(viewer.playerId) ?: return Reply.stop(if (route.field?.text == "score") 0 else "no clan")
        return Reply.next(DuelWar(clan.war))
    }

    /** `onInvalid` of a branch covers the text placeholders below it: `{duel.lost.name}` shows nothing instead of the address. */
    @Placeholder(onInvalid = Invalid.EMPTY)
    fun lost(): Reply<DuelWar> = Reply.none()

    /** Looking at the parts: formats and arguments are there too. */
    @Placeholder
    fun explain(@Route route: RouteInfo): Reply<String> =
        Reply.of(route.parts.joinToString(", ") { "${it.kind.name.lowercase()}=${it.text}" } +
            if (route.of(RouteKind.FORMAT).isEmpty()) "" else " (formats: ${route.of(RouteKind.FORMAT).joinToString { it.name }})")
}

internal class DuelWar(private val info: WarInfo) {
    @Placeholder val score: Reply<Int> get() = Reply.of(info.score)

    @Placeholder val wins: Reply<Int> get() = Reply.of(info.wins)
}

// Every way to read the route. Registered like any group: `placeholders.register(RouteTour())`.
// Ask `{route.tour.top.3.name|pad0:4|upper}` and each method below sees the same parts.

@Placeholders("route")
internal class RouteTour {

    /** All parts in the order written, each with its kind: `{route.show.x}` gives the whole list. */
    @Placeholder
    fun show(@Route route: RouteInfo, @Argument text: ArgumentValue<String>): Reply<String> =
        Reply.of(route.parts.joinToString(" ") { "${it.kind}:${it.text}" }) // GROUP:route BRANCH:show ARGUMENT:x

    /** Short readable text of the route, good for logs and error messages. */
    @Placeholder
    fun log(@Route route: RouteInfo): Reply<String> = Reply.of(route.toString()) // group:route.field:log

    /** The address without formats, and the group. `{route.path}` gives `route.path`, group `route`. */
    @Placeholder
    fun path(@Route route: RouteInfo): Reply<String> = Reply.of("${route.path} in ${route.group}")

    /** The last word with the value: it tells which field of a branch is read. */
    @Placeholder
    fun top(@Route route: RouteInfo, @Argument place: ArgumentValue<Int>): Reply<TopEntry> {
        val field = route.field?.text // `name` for {route.top.3.name}, `kills` for {route.top.3.kills}
        val argument = route.of(RouteKind.ARGUMENT).single() // the `3`: kind ARGUMENT, text "3", name = the parameter name
        if (place.required() > 10) return Reply.stop("$field of place ${argument.text}: nobody") // ends here, with a text
        return Reply.next(TopEntry(place.required()))
    }

    /** Formats written after the bar, with their arguments: `{route.formats|pad0:4|upper}`. */
    @Placeholder
    fun formats(@Route route: RouteInfo): Reply<String> {
        val written = route.of(RouteKind.FORMAT).joinToString { "${it.name}${it.arguments.joinToString(",", "(", ")")}" }
        return Reply.value(if (route.hasFormat("raw")) "raw: $written" else written) // pad0(4), upper()
    }

    /** What lies below a branch: the parts after the first part called `deep`. */
    @Placeholder
    fun deep(@Route route: RouteInfo): Reply<DeepBranch> = Reply.next(DeepBranch(route.after("deep").joinToString(".") { it.text }))
}

internal class TopEntry(private val place: Int) {
    @Placeholder val name: Reply<String> get() = Reply.of("player$place")

    @Placeholder val kills: Reply<Int> get() = Reply.of(100 - place)
}

internal class DeepBranch(private val below: String) {
    /** `{route.deep.ending}`: `below` is `ending`, the parts after `deep`. */
    @Placeholder val ending: Reply<String> get() = Reply.of(below)
}
