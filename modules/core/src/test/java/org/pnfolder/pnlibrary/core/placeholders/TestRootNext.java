package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** {@code <имя>.wins} right under the prefix. */
@Placeholders(root = true)
public class TestRootNext {
    @Placeholder(bare = true)
    public Reply<Stats> player(@Argument ArgumentValue<String> name) {
        return Reply.next(new Stats(name.required()));
    }

    public static final class Stats {
        private final String name;

        Stats(String name) {
            this.name = name;
        }

        @Placeholder
        public Reply<String> wins() {
            return Reply.value(name + "-wins");
        }
    }
}
