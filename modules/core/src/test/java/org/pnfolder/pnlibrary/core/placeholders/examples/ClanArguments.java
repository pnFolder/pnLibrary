package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Group;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.PlayerRef;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

// Example 2: arguments.
//
// In an address there is only text. A parameter of type ArgumentValue<T> receives it converted to T.
// The ArgumentValue keeps the text too, so the method can still answer when the conversion failed.

@Placeholders("stats")
public class ClanArguments {

    /** {@code {stats.top.3}}. A number outside 1..100 is not a value, and the address stays as it is. */
    @Placeholder
    public Reply<String> top(@Argument(range = "1..100") ArgumentValue<Integer> place) {
        List<Member> byKills = new ArrayList<Member>(Clans.wolves().getMembers());
        Collections.sort(byKills, new Comparator<Member>() {
            @Override
            public int compare(Member a, Member b) {
                return Integer.compare(b.getKills(), a.getKills());
            }
        });
        int index = place.required() - 1;
        return Reply.of(index >= 0 && index < byKills.size() ? byKills.get(index).getName() : null);
    }

    /** {@code {stats.kills.Alex.week}}. Words of an enum are offered and checked by themselves. */
    @Placeholder
    public Reply<Integer> kills(@Argument ArgumentValue<PlayerRef> who, @Argument ArgumentValue<Period> period) {
        for (Member member : Clans.wolves().getMembers()) {
            if (member.getName().equalsIgnoreCase(who.required().getText())) {
                return Reply.of(member.getKills() * (period.required() == Period.WEEK ? 1 : 4));
            }
        }
        throw new java.util.NoSuchElementException("No member called " + who.getText());
    }

    /** {@code {stats.sort.kills}}. A fixed list of words for text. */
    @Placeholder
    public Reply<String> sort(@Argument(choices = {"kills", "name"}) ArgumentValue<String> by) {
        List<Member> sorted = new ArrayList<Member>(Clans.wolves().getMembers());
        final boolean byKills = by.required().equals("kills");
        Collections.sort(sorted, new Comparator<Member>() {
            @Override
            public int compare(Member a, Member b) {
                return Long.compare(key(a), key(b));
            }

            private long key(Member member) {
                return byKills ? -(long) member.getKills() : (long) member.getName().hashCode();
            }
        });
        return Reply.of(sorted.get(0).getName());
    }

    /** {@code {stats.friend.Bob}}. The player is not known: the method shows its own answer instead of failing. */
    @Placeholder
    public Reply<String> friend(@Argument ArgumentValue<PlayerRef> who) {
        for (Member member : Clans.wolves().getMembers()) {
            if (member.getName().equalsIgnoreCase(who.getText())) {
                return Reply.of(member.getName());
            }
        }
        return Reply.of("Nobody called " + who.getText());
    }

    /** {@code {stats.mine.me}} is the one who looks, {@code {stats.mine.Mia}} is Mia. */
    @Placeholder
    public Reply<String> mine(@Argument ArgumentValue<PlayerRef> who, @Viewer ViewerInfo viewer) {
        PlayerRef player = who.orMe(viewer);
        return Reply.of(player == null ? null : player.getText());
    }

    /** {@code {stats.age.1h30m}}. Durations are written as 90s, 15m, 1h30m or 2d. */
    @Placeholder
    public Reply<Long> age(@Argument ArgumentValue<Duration> span) {
        return Reply.of(span.required().getSeconds());
    }

    /** {@code {stats.find.wol}}. Plain text is not converted: the method searches by itself. */
    @Placeholder
    public Reply<String> find(@Argument ArgumentValue<String> query) {
        for (String name : new String[] {"Wolves", "Owls"}) {
            if (name.toLowerCase(java.util.Locale.ROOT).contains(query.getText().toLowerCase(java.util.Locale.ROOT))) {
                return Reply.of(name);
            }
        }
        return Reply.of(null);
    }

    /** {@code {stats.own.Wolves}}. A type of the plugin works too once the converter is registered (see {@link ClanSetup}). */
    @Placeholder
    public Reply<Integer> own(@Argument ArgumentValue<Clan> clan) {
        return Reply.of(clan.required().getMembers().size());
    }

    /** When there is no value, show a fixed text instead of leaving the address in the text. */
    @Placeholder(onInvalid = Invalid.DEFAULT, defaultText = "no clan")
    public Reply<String> clanOf(@Argument ArgumentValue<Clan> clan) {
        return Reply.of(clan.getValue() == null ? null : clan.getValue().getName());
    }

    /** Several arguments in one class, so that the signature stays short: {@code {stats.query.Alex.week}}. */
    @Placeholder
    public Reply<String> query(@Group KillsQuery query) {
        return Reply.of(query.who.required().getText() + "-" + query.period.required());
    }
}
