package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.concurrent.CompletableFuture;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

// Example 9: Reply. A method says what it returns: an answer, a way to go on, or nothing.
//
//   Reply.value(x)          an answer: the address ends here               {clan.level}
//   Reply.value(x, format)  an answer that brings its own format           {clan.balance} gives 1 250 coins
//   Reply.next(object)      go on: the address continues in the object     {clan.war.score}
//   Reply.stop(x)           answer from a branch: nothing below is called  {clan.war.score} gives "no clan"
//   Reply.none()            no value: the address stays as written
//
// The type tells what the method is: Reply<Integer> is a value, Reply<WarInfo> is a branch.
// Every placeholder method returns a Reply (or a CompletableFuture of one): a plain return type is refused at registration.
// Reply.of(x) is a value when x is there and none when it is null; Reply.next(x) the same for an object.

@Placeholders("clan")
public class ClanReplies {

    /** An answer, or nothing when the player has no clan. For Bob {@code {clan.level}} stays as it is. */
    @Placeholder("Level of the clan")
    public Reply<Integer> level(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        if (clan == null) {
            return Reply.none();
        }
        return Reply.value(clan.getLevel());
    }

    /** An answer with its own format: {@code {clan.balance}} gives {@code 1250000 $}, {@code {clan.balance|raw}} gives the number. */
    @Placeholder("Balance of the clan")
    public Reply<Long> balance(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        if (clan == null) {
            return Reply.none();
        }
        return Reply.value(clan.getBalance(), "money");
    }

    /** A branch. Alex: {@code {clan.war.score}} gives 340. Bob: {@code {clan.war.score}} and {@code {clan.war.wins}} give "no clan". */
    @Placeholder("War of the clan")
    public Reply<WarInfo> war(@Viewer ViewerInfo viewer) {
        Clan clan = Clans.of(viewer.getPlayerId());
        if (clan == null) {
            return Reply.stop("no clan");
        }
        return Reply.next(clan.getWar());
    }

    /** The same branch, answered later (a database): {@code CompletableFuture<Reply<...>>}. */
    @Placeholder("War of the clan, read in the background")
    public CompletableFuture<Reply<WarInfo>> warLater(@Viewer final ViewerInfo viewer) {
        return CompletableFuture.supplyAsync(() -> {
            Clan clan = Clans.of(viewer.getPlayerId());
            return clan == null ? Reply.<WarInfo>stop("no clan") : Reply.next(clan.getWar());
        });
    }
}
