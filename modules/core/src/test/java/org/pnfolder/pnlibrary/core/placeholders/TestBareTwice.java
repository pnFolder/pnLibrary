package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("twice")
public class TestBareTwice {
    @Placeholder(bare = true)
    public Reply<String> first(@Argument ArgumentValue<String> name) {
        return Reply.value(name.required());
    }

    @Placeholder(bare = true)
    public Reply<String> second(@Argument ArgumentValue<String> name) {
        return Reply.value(name.required());
    }
}
