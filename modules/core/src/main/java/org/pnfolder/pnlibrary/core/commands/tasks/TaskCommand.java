package org.pnfolder.pnlibrary.core.commands.tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/**
 * {@code /pn tasks}: the scheduled tasks of every plugin, drawn like the other {@code /pn} pages.
 *
 * <pre>
 * /pn tasks [today|week|older|any] [all|active|failed|done] [page]   tasks with tabs by date and by state
 * /pn tasks view &lt;id&gt;     one task: what it is for, owner, how it runs, times, rights and the last failure
 * /pn tasks run &lt;id&gt;      starts a waiting task at once, unless its plugin forbids it
 * /pn tasks cancel &lt;id&gt;   stops a task that has not finished, unless its plugin forbids it
 * </pre>
 *
 * A task may carry a description and a usage note given by its plugin, and may be hidden from this command. The id may
 * be shortened to its first characters. Requires {@code pnlibrary.tasks}; the console always may use it.
 */
public final class TaskCommand {
    public static final String PERMISSION = "pnlibrary.tasks";
    private static final String ROOT = "/pn tasks";
    private static final int PAGE_SIZE = 6;
    private static final int DESCRIPTION_PREVIEW = 70;
    private static final int CHILD_PREVIEW = 3;
    private static final Comparator<TaskSnapshot> NEWEST_FIRST = new Comparator<TaskSnapshot>() {
        @Override
        public int compare(TaskSnapshot left, TaskSnapshot right) {
            return TaskLabels.activity(right).compareTo(TaskLabels.activity(left));
        }
    };

    private final PnLibrary library;
    private final TaskListPage page;
    private final TaskCard card;

    public TaskCommand(@NotNull PnLibrary library) {
        this.library = library;
        this.page = new TaskListPage(library);
        this.card = new TaskCard(library);
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("tasks", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Задачи плагинов доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> page.list(context, TaskPeriod.TODAY, TaskState.ALL, 1));
            for (final TaskPeriod period : TaskPeriod.values()) {
                root.literal(period.getWord(), node -> periodWords(node, period));
            }
            for (final TaskState state : TaskState.values()) {
                root.literal(state.getWord(), node -> paged(node, TaskPeriod.ANY, state));
            }
            idCommand(root, "view", task -> true, this::view);
            idCommand(root, "run", task -> task.getStatus() == TaskStatus.SCHEDULED && task.getRunnableNow(), this::run);
            idCommand(root, "cancel", TaskSnapshot::isActive, this::cancel);
        });
    }

    private void periodWords(CommandNodeBuilder node, TaskPeriod period) {
        paged(node, period, TaskState.ALL);
        for (final TaskState state : TaskState.values()) {
            node.literal(state.getWord(), inner -> paged(inner, period, state));
        }
    }

    private void paged(CommandNodeBuilder node, final TaskPeriod period, final TaskState state) {
        node.executes(context -> page.list(context, period, state, 1));
        node.argument("page", ArgumentType.integer(),
                argument -> argument.executes(context -> page.list(context, period, state, context.<Integer>get("page"))));
    }

    private void idCommand(CommandBuilder root, String word,
            final Predicate<TaskSnapshot> filter, final BiConsumer<CommandContext, String> action) {
        root.literal(word, node -> node.argument("id", ArgumentType.string(), id -> {
            id.suggests(context -> shortIds(filter));
            id.executes(context -> action.accept(context, context.<String>get("id")));
        }));
    }

    private List<String> shortIds(Predicate<TaskSnapshot> filter) {
        List<String> ids = new ArrayList<String>();
        for (TaskSnapshot task : RuntimeBridge.taskSnapshots(library)) {
            if (filter.test(task)) {
                String id = task.getId().getValue();
                ids.add(id.length() <= TaskLabels.SHORT_ID ? id : id.substring(0, TaskLabels.SHORT_ID));
            }
        }
        return ids;
    }

    private void withTask(CommandContext context, String typed, BiConsumer<ChatKit, TaskSnapshot> action) {
        ChatKit kit = CommandOutput.kit(context);
        TaskSnapshot found = null;
        int matches = 0;
        for (TaskSnapshot task : RuntimeBridge.taskSnapshots(library)) {
            if (task.getId().getValue().regionMatches(true, 0, typed, 0, typed.length())) {
                found = task;
                matches++;
            }
        }
        if (matches != 1) {
            context.getSender().sendMessage(kit.text("Такой задачи нет или id неоднозначен: " + typed + ". Список: " + ROOT,
                    ChatPalette.DANGER));
            return;
        }
        action.accept(kit, found);
    }

    private void view(CommandContext context, String typed) {
        withTask(context, typed, (kit, task) -> CommandOutput.send(context, card.lines(kit, task)));
    }

    private void cancel(final CommandContext context, String typed) {
        withTask(context, typed, (kit, task) -> {
            String title = TaskLabels.title(task);
            TaskControl result = RuntimeBridge.cancelTask(library, task.getId().getValue());
            context.getSender().sendMessage(answer(kit, result, "Задача " + title + " остановлена.",
                    "Плагин не разрешает останавливать задачу " + title + ".", "Задача " + title + " уже не работает."));
        });
    }

    private void run(final CommandContext context, String typed) {
        withTask(context, typed, (kit, task) -> {
            String title = TaskLabels.title(task);
            TaskControl result = RuntimeBridge.runTaskNow(library, task.getId().getValue());
            context.getSender().sendMessage(answer(kit, result, "Задача " + title + " запущена.",
                    "Плагин не разрешает запускать задачу " + title + " вручную.",
                    "Задачу " + title + " сейчас запустить нельзя: она не ждёт срока."));
        });
    }

    private Component answer(ChatKit kit, TaskControl result, String done, String forbidden, String unavailable) {
        switch (result) {
            case DONE:
                return kit.text(done, ChatPalette.OK);
            case FORBIDDEN:
                return kit.text(forbidden, ChatPalette.DANGER);
            default:
                return kit.text(unavailable, ChatPalette.SOFT);
        }
    }
}
