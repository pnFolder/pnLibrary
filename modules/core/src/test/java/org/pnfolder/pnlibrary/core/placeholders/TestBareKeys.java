package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** {@code keys.<case>} and {@code keys.total}: the argument stands right after the group. */
@Placeholders("keys")
public class TestBareKeys {
    @Placeholder(bare = true)
    public Reply<Integer> keys(@Argument ArgumentValue<String> name) {
        return Reply.value(name.required().length());
    }

    @Placeholder
    public Reply<Integer> total() {
        return Reply.value(99);
    }
}
