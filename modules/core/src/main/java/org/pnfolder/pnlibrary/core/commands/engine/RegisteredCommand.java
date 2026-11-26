package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

/** One command registered through {@link CommandRegistry}; closing it removes it everywhere it was recorded. */
final class RegisteredCommand implements CommandRegistration {
    private final Object owner;
    private final CommandDefinition command;
    private final Set<String> names;
    private final PlatformCommandRegistration nativeRegistration;
    private final CommandFeedbackScope feedback;
    private final CommandIndex index;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    RegisteredCommand(Object owner, CommandDefinition command, Set<String> names,
            PlatformCommandRegistration nativeRegistration, @Nullable CommandFeedbackScope feedback, CommandIndex index) {
        this.owner = owner;
        this.command = command;
        this.names = names;
        this.nativeRegistration = nativeRegistration;
        this.feedback = feedback;
        this.index = index;
    }

    Object getOwner() {
        return owner;
    }

    @Nullable
    CommandFeedbackScope getFeedback() {
        return feedback;
    }

    @NotNull
    @Override
    public CommandDefinition getCommand() {
        return command;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        index.remove(this, names);
        nativeRegistration.close();
    }
}
