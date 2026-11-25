package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.util.concurrent.CompletableFuture

// Example 9: Reply. A method says what it returns: an answer, a way to go on, or nothing.
//
//   Reply.value(x)          an answer: the address ends here               {clan.level}
//   Reply.value(x, format)  an answer that brings its own format           {clan.balance} gives 1 250 coins
//   Reply.next(object)      go on: the address continues in the object     {clan.war.score}
//   Reply.stop(x)           answer from a branch: nothing below is called  {clan.war.score} gives "no clan"
//   Reply.none()            no value: the address stays as written
//
// The type tells what the method is: Reply<Int> is a value, Reply<WarInfo> is a branch.
// Every placeholder method returns a Reply (or a CompletableFuture of one): a plain return type is refused at registration.
// `Reply.of(x)` is a value when x is there and none when it is null; `Reply.next(x)` the same for an object.

@Placeholders("clan")
internal class ClanReplies {

    /** An answer, or nothing when the player has no clan. For Bob `{clan.level}` stays as it is. */
    @Placeholder("Level of the clan")
    fun level(@Viewer viewer: ViewerInfo): Reply<Int> {
        val clan = Clans.of(viewer.playerId) ?: return Reply.none()
        return Reply.value(clan.level)
    }

    /** An answer with its own format: `{clan.balance}` gives `1250000 $`, `{clan.balance|raw}` gives the number. */
    @Placeholder("Balance of the clan")
    fun balance(@Viewer viewer: ViewerInfo): Reply<Long> {
        val clan = Clans.of(viewer.playerId) ?: return Reply.none()
        return Reply.value(clan.balance, format = "money")
    }

    /** A branch. Alex: `{clan.war.score}` gives 340. Bob: `{clan.war.score}` and `{clan.war.wins}` give "no clan". */
    @Placeholder("War of the clan")
    fun war(@Viewer viewer: ViewerInfo): Reply<WarInfo> {
        val clan = Clans.of(viewer.playerId) ?: return Reply.stop("no clan")
        return Reply.next(clan.war)
    }

    /** The same branch, answered later (a database): `CompletableFuture<Reply<...>>`. */
    @Placeholder("War of the clan, read in the background")
    fun warLater(@Viewer viewer: ViewerInfo): CompletableFuture<Reply<WarInfo>> = CompletableFuture.supplyAsync {
        val clan = Clans.of(viewer.playerId)
        if (clan == null) Reply.stop("no clan") else Reply.next(clan.war)
    }
}
