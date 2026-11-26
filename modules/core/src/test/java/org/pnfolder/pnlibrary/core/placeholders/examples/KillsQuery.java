package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.PlayerRef;

/** No annotation on the class: the parameter that takes it is marked with {@code @Group}. */
public class KillsQuery {
    public final ArgumentValue<PlayerRef> who;
    public final ArgumentValue<Period> period;

    public KillsQuery(@Argument ArgumentValue<PlayerRef> who, @Argument ArgumentValue<Period> period) {
        this.who = who;
        this.period = period;
    }
}
