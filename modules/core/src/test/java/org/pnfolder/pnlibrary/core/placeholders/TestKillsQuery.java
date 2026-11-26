package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.PlayerRef;

public class TestKillsQuery {
    public final ArgumentValue<PlayerRef> who;
    public final ArgumentValue<String> period;

    public TestKillsQuery(@Argument ArgumentValue<PlayerRef> who, @Argument ArgumentValue<String> period) {
        this.who = who;
        this.period = period;
    }
}
