package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** {@code <кейс>} right under the prefix, and {@code total} next to it. */
@Placeholders(root = true)
public class TestRootCases {
    @Placeholder(bare = true)
    public Reply<Integer> cases(@Argument ArgumentValue<String> name) {
        return Reply.value(name.required().length());
    }

    @Placeholder
    public Reply<Integer> total() {
        return Reply.value(99);
    }
}
