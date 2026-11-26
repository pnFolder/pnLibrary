package org.pnfolder.pnlibrary.core.expansions;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.expansions.Expansion;

public interface ModuleBoundExpansion<V> extends Expansion {
    /** Creates the view for one module, once per module; whatever it acquires for the module goes through {@link ModuleBinding#own}. */
    @NotNull V bind(@NotNull ModuleBinding module);
}
