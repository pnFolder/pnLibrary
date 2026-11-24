package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Hide
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo

// Example 1: the basics.
//
// The address is {group.branch.argument.field|format|format}.
// A class marked with @Placeholders is the group. Every @Placeholder member is a branch of it.

/** {clan.level} gives 12 for a player of the clan; {clan.war.score} gives 340; {clan.tag} gives WLF. */
@Placeholders("clan")
internal class ClanBasics {

    /** A value. The description in the brackets is what `/pn placeholders` shows. */
    @Placeholder("Level of the clan")
    fun level(@Viewer viewer: ViewerInfo): Reply<Int> = Reply.of(Clans.of(viewer.playerId)?.level)

    /** A method that returns an object is a branch: the @Placeholder members of [WarBranch] continue the address. */
    @Placeholder
    fun war(@Viewer viewer: ViewerInfo): Reply<WarBranch> = Reply.next(Clans.of(viewer.playerId)?.war?.let(::WarBranch))

    /** A property works the same way: `{clan.tag}`. */
    @Placeholder
    val tag: Reply<String> = Reply.of("WLF")

    /** Marked with @Hide, so it is not a placeholder even though it has @Placeholder. */
    @Hide
    @Placeholder
    fun secret(): Reply<String> = Reply.of("never shown")
}

/** Reached through `war`, so the addresses are `{clan.war.score}` and `{clan.war.wins}`. No registration needed. */
internal class WarBranch(private val info: WarInfo) {
    @Placeholder
    val score: Reply<Int> get() = Reply.of(info.score)

    @Placeholder
    val wins: Reply<Int> get() = Reply.of(info.wins)
}
