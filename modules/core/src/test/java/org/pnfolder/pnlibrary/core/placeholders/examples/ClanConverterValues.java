package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("clanlist")
public class ClanConverterValues {

    /** {@code {clanlist.members.Wolves}}. */
    @Placeholder
    public Reply<Integer> members(@Argument(converter = ClanByName.class) ArgumentValue<Clan> clan) {
        return Reply.of(clan.required().getMembers().size());
    }

    /** {@code {clanlist.level.Wol}}: the same type, read by tag. */
    @Placeholder
    public Reply<Integer> level(@Argument(converter = ClanByTag.class) ArgumentValue<Clan> clan) {
        return Reply.of(clan.required().getLevel());
    }

    /** {@code {clanlist.later.Wolves}}. The answer is calculated in the background. */
    @Placeholder
    public Reply<Long> later(@Argument(converter = ClanFromDatabase.class) ArgumentValue<Clan> clan) {
        return Reply.of(clan.required().getBalance());
    }

    /** A text that does not fit is not lost: the method sees the reason and may answer by itself. */
    @Placeholder
    public Reply<String> known(@Argument(converter = ClanByName.class) ArgumentValue<Clan> clan) {
        if (clan.getValue() != null) {
            return Reply.of(clan.getValue().getName());
        }
        return Reply.of(clan.getError() != null ? clan.getError() : "unknown");
    }

    /** {@code {clanlist.compare.Wolves.Owls}}. */
    @Placeholder
    public Reply<Integer> compare(
            @Argument(name = "first", converter = ClanByName.class) ArgumentValue<Clan> first,
            @Argument(name = "second", converter = OtherClan.class) ArgumentValue<Clan> second) {
        return Reply.of(first.required().getLevel() - second.required().getLevel());
    }
}
