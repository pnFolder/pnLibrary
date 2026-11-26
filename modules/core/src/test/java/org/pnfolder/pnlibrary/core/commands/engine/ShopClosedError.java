package org.pnfolder.pnlibrary.core.commands.engine;

import org.pnfolder.pnlibrary.api.commands.error.CommandError;

/** An error that knows what to say when nobody answers it. */
final class ShopClosedError extends CommandError {
    ShopClosedError() {
        super("Магазин закрыт");
    }
}
