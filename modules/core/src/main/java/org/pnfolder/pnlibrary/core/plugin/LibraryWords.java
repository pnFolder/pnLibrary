package org.pnfolder.pnlibrary.core.plugin;

import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.currency.BalanceRequirement;
import org.pnfolder.pnlibrary.api.currency.ChargeAction;
import org.pnfolder.pnlibrary.api.currency.GiveAction;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;

/** Configuration words the library itself adds for every plugin; a plugin may still publish its own word with a higher priority. */
public final class LibraryWords {
    private LibraryWords() {
    }

    public static void register(ConfigScope scope) {
        scope.type(Action.class, ChargeAction.class).name("charge").everywhere().register();
        scope.type(Action.class, GiveAction.class).name("give").everywhere().register();
        scope.type(Requirement.class, BalanceRequirement.class).name("balance").everywhere().register();
    }
}
