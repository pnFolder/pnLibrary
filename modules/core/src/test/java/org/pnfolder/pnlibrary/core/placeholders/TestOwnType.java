package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("own")
public class TestOwnType {
    @Placeholder
    public Reply<Integer> clanOf(@Argument ArgumentValue<TestClan> clan) {
        return Reply.of(clan.required().getName().length());
    }
}
