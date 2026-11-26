package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Native registration boundary used once the shared runtime has been created. */
public interface PlatformBinding {
    /** Called once with the created {@code library} so the platform can register itself. */
    default void bind(@NotNull PnLibrary library) {
    }
}
