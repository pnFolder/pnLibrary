package org.pnfolder.pnlibrary.core.stats;

import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/**
 * {@code /pn stats}: the numbers of the server right now (tasks, events, warnings and errors of the day) and how they
 * looked at the last few hourly snapshots kept in the journal. Requires {@code pnlibrary.stats}; the console always may.
 */
final class StatsCommand {
    private static final String PERMISSION = "pnlibrary.stats";
    private static final long HISTORY_DAYS = 7L;
    private static final int HISTORY_ROWS = 6;

    private StatsCommand() {
    }

    static @NotNull CommandDefinition create(@NotNull Supplier<StatsSnapshot> snapshot, @NotNull StatsRecorder recorder) {
        return CommandDefinition.command("stats", command -> {
            command.permission(PERMISSION);
            command.noPermissionMessage("Статистика доступна администраторам. Нужно право " + PERMISSION + ".");
            command.consoleBypassesPermission();
            command.executes(context -> {
                for (Component line : StatsLines.of(snapshot.get(), recorder.history(HISTORY_DAYS, HISTORY_ROWS))) {
                    context.getSender().sendMessage(line);
                }
            });
        });
    }
}
