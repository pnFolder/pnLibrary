package org.pnfolder.pnlibrary.core.expansions;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

/** Layouts a module registers end with it; closing the view does nothing. */
final class ModuleKeyboardLayouts implements KeyboardLayouts {
    private final KeyboardLayouts shared;
    private final ModuleBinding module;

    ModuleKeyboardLayouts(KeyboardLayouts shared, ModuleBinding module) {
        this.shared = shared;
        this.module = module;
    }

    @Override
    public @NotNull AutoCloseable register(@NotNull KeyboardLayout layout) {
        return module.own(() -> shared.register(layout));
    }

    @Override
    public @NotNull List<KeyboardLayout> all() {
        return shared.all();
    }

    @Override
    public void close() {
    }
}
