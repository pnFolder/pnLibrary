package org.pnfolder.pnlibrary.core.runtime.platform;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRegistry;
import org.pnfolder.pnlibrary.core.commands.engine.StandardCommandFeedback;
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks;
import org.pnfolder.pnlibrary.core.tasks.TaskErrorLogger;
import org.pnfolder.pnlibrary.core.tasks.TaskHistoryStore;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

/** Services built on top of platform adapters: commands, audiences and tasks. */
public final class PlatformServiceSet {
    private final ScheduledTasks tasks;
    private final CommandRegistry commands;
    private final PlatformAudiences audiences;

    public PlatformServiceSet(@NotNull PlatformCommandAdapter commandAdapter, @NotNull PlatformAudienceAdapter playerAdapter,
                              @NotNull PlatformTaskAdapter taskAdapter, @NotNull PlatformLogging platformLogging,
                              int taskHistoryCapacity, @Nullable Path taskHistoryFile, @NotNull TaskErrorLogger errorLogger,
                              @NotNull StandardCommandFeedback commandFeedback, @NotNull Consumer<Throwable> onPlayerFailure) {
        this.tasks = new ScheduledTasks(taskAdapter, new TaskServiceSettings(taskHistoryCapacity), errorLogger,
                taskHistoryFile == null ? null : new TaskHistoryStore(taskHistoryFile));
        this.commands = new CommandRegistry(commandAdapter, platformLogging, commandFeedback);
        this.audiences = new PlatformAudiences(playerAdapter, onPlayerFailure);
    }

    public @NotNull ScheduledTasks getTasks() {
        return tasks;
    }

    public @NotNull CommandRegistry getCommands() {
        return commands;
    }

    public @NotNull PlatformAudiences getAudiences() {
        return audiences;
    }
}
