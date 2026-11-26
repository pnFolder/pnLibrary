package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.CommandFeedbackScope;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;

/** The commands one module registered, so closing the module takes them all back. */
public final class ModuleCommands {
    private final Object owner;
    private final CommandService commands;
    private final CommandFeedbackScope feedback;
    private final BooleanSupplier closed;
    private final List<CommandRegistration> registrations = new ArrayList<CommandRegistration>();

    public ModuleCommands(@NotNull Object owner, @Nullable CommandService commands, @NotNull CommandFeedbackScope feedback,
                          @NotNull BooleanSupplier closed) {
        this.owner = owner;
        this.commands = commands;
        this.feedback = feedback;
        this.closed = closed;
    }

    public @NotNull CommandRegistration register(@NotNull CommandDefinition command) {
        synchronized (registrations) {
            if (closed.getAsBoolean()) {
                throw new IllegalStateException("Module is closed");
            }
            if (commands == null) {
                throw new IllegalStateException("Commands are unavailable");
            }
            CommandRegistration registration = commands.register(owner, command, feedback);
            if (closed.getAsBoolean()) {
                registration.close();
                throw new IllegalStateException("Module is closed");
            }
            for (Iterator<CommandRegistration> it = registrations.iterator(); it.hasNext(); ) {
                if (it.next().isClosed()) {
                    it.remove();
                }
            }
            registrations.add(registration);
            return registration;
        }
    }

    /** Hands over every registration still held and forgets them. */
    public @NotNull List<CommandRegistration> drain() {
        synchronized (registrations) {
            List<CommandRegistration> held = new ArrayList<CommandRegistration>(registrations);
            registrations.clear();
            return held;
        }
    }
}
