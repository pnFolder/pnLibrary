package org.pnfolder.pnlibrary.bukkit.audience;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

/** Builds the journal notice off the server thread and sends it from the player's own thread. */
public final class JoinNoticeDelivery {
    private final TaskScope tasks;

    public JoinNoticeDelivery(TaskScope tasks) {
        this.tasks = tasks;
    }

    /** Reading the journal can take long, so {@code build} runs asynchronously; only {@code send} touches the player. */
    public void deliver(Player player, Supplier<List<Component>> build, Consumer<List<Component>> send) {
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.async())
            .delay(Duration.ofMillis(50))
            .action(context -> {
                List<Component> lines = build.get();
                if (!lines.isEmpty()) {
                    tasks.schedule(sendTask(player, lines, send));
                }
            })
            .build());
    }

    private TaskSpec sendTask(Player player, List<Component> lines, Consumer<List<Component>> send) {
        return TaskSpec.builder()
            .execution(TaskExecution.entity(player))
            .action(context -> {
                if (player.isOnline()) {
                    send.accept(lines);
                }
            })
            .build();
    }
}
