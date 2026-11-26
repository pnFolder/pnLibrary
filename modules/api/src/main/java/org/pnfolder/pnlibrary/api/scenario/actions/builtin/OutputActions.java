package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

/** Output never fails a scenario: a platform that could not deliver gets a warning and the scenario carries on. */
final class OutputActions {
    private OutputActions() {
    }

    static ActionResult warnUnless(ActionContext context, Delivery delivery, String what) {
        switch (delivery.getStatus()) {
            case DELIVERED:
                break;
            case UNSUPPORTED:
                context.getLogger().warning(what + " is not supported by the current platform");
                break;
            default:
                Throwable failure = delivery.getFailure();
                String reason = failure != null && failure.getMessage() != null ? failure.getMessage() : String.valueOf(delivery.getStatus());
                context.getLogger().warning(what + " failed: " + reason);
                break;
        }
        return ActionResult.Done.INSTANCE;
    }
}
