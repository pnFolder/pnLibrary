package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** {@code player.<name>.<key>}: a bare member returns an object whose keys are ordinary placeholders. */
@Placeholders("player")
public class TestBarePlayer {
    @Placeholder(bare = true)
    public Reply<Stats> player(@Argument ArgumentValue<String> name) {
        return Reply.next(new Stats(name.required()));
    }

    public static class Stats {
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
