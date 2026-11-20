package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.type
import org.pnfolder.pnlibrary.api.currency.BalanceRequirement
import org.pnfolder.pnlibrary.api.currency.ChargeAction
import org.pnfolder.pnlibrary.api.currency.GiveAction
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement

/** Configuration words the library itself adds for every plugin; a plugin may still publish its own word with a higher priority. */
internal object LibraryWords {
    fun register(scope: ConfigScope) {
        scope.type<Action, ChargeAction>().name("charge").everywhere().register()
        scope.type<Action, GiveAction>().name("give").everywhere().register()
        scope.type<Requirement, BalanceRequirement>().name("balance").everywhere().register()
    }
}
