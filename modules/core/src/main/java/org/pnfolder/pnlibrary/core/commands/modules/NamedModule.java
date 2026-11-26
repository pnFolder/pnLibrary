package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

/** A module with the text that names it in commands: {@code plugin:module}. */
public final class NamedModule {
    private final String name;
    private final ModuleContext module;

    public NamedModule(@NotNull String name, @NotNull ModuleContext module) {
        this.name = Objects.requireNonNull(name, "name");
        this.module = Objects.requireNonNull(module, "module");
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public ModuleContext getModule() {
        return module;
    }
}
