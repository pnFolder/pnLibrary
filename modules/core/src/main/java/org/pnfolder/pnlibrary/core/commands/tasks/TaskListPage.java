package org.pnfolder.pnlibrary.core.commands.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.page.CommandHint;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/** The list page of {@code /pn tasks}: the tabs by date and state, one row per task and the subtasks under it. */
final class TaskListPage {
    private static final String ROOT = "/pn tasks";
    private static final int PAGE_SIZE = 6;
    private static final int DESCRIPTION_PREVIEW = 70;
    private static final int CHILD_PREVIEW = 3;
    private static final Comparator<TaskSnapshot> NEWEST_FIRST = (left, right) -> TaskLabels.activity(right).compareTo(TaskLabels.activity(left));

    private final PnLibrary library;

    TaskListPage(PnLibrary library) {
        this.library = library;
    }

    void list(CommandContext context, TaskPeriod period, TaskState state, int requestedPage) {
        ChatKit kit = CommandOutput.kit(context);
        List<TaskSnapshot> everything = new ArrayList<TaskSnapshot>(RuntimeBridge.taskSnapshots(library));
        Collections.sort(everything, NEWEST_FIRST);
        List<TaskSnapshot> all = TaskLabels.roots(everything);
        List<TaskSnapshot> inPeriod = new ArrayList<TaskSnapshot>();
        for (TaskSnapshot task : all) {
            if (period.keep(task)) {
                inPeriod.add(task);
            }
        }
        List<TaskSnapshot> shown = new ArrayList<TaskSnapshot>();
        for (TaskSnapshot task : inPeriod) {
            if (state.keep(task)) {
                shown.add(task);
            }
        }
        int pages = ChatPager.pageCount(shown.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requestedPage, pages);
        List<Component> lines = listHeader(kit, period, state, all, inPeriod);
        if (shown.isEmpty()) {
            lines.add(kit.note("Здесь пока пусто.", ChatPalette.FAINT));
        }
        for (TaskSnapshot task : ChatPager.slice(shown, page, PAGE_SIZE)) {
            lines.addAll(rowWithChildren(kit, task, everything));
        }
        lines.addAll(listFooter(kit, period, state, page, pages));
        CommandOutput.send(context, lines);
    }


    private List<Component> listHeader(ChatKit kit, final TaskPeriod period, final TaskState state, final List<TaskSnapshot> all,
            final List<TaskSnapshot> inPeriod) {
        String count = all.size() + " " + RussianText.plural(all.size(), "задача", "задачи", "задач");
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header("Задачи плагинов", kit.text(count, ChatPalette.LABEL)));
        lines.add(tabs(kit, "Когда", Arrays.asList(TaskPeriod.values()), period, TaskPeriod::getLabel,
                choice -> countKept(all, choice), choice -> ROOT + " " + choice.getWord() + " " + state.getWord()));
        lines.add(tabs(kit, "Какие", Arrays.asList(TaskState.values()), state, TaskState::getLabel,
                choice -> countKept(inPeriod, choice), choice -> ROOT + " " + period.getWord() + " " + choice.getWord()));
        lines.add(kit.blank());
        return lines;
    }


    private static int countKept(List<TaskSnapshot> tasks, TaskPeriod period) {
        int count = 0;
        for (TaskSnapshot task : tasks) {
            if (period.keep(task)) {
                count++;
            }
        }
        return count;
    }


    private static int countKept(List<TaskSnapshot> tasks, TaskState state) {
        int count = 0;
        for (TaskSnapshot task : tasks) {
            if (state.keep(task)) {
                count++;
            }
        }
        return count;
    }


    private List<Component> rowWithChildren(ChatKit kit, TaskSnapshot task, List<TaskSnapshot> everything) {
        List<Component> lines = new ArrayList<Component>();
        lines.add(row(kit, task, everything));
        List<TaskSnapshot> children = TaskLabels.childrenOf(task, everything);
        for (TaskSnapshot child : children.subList(0, Math.min(CHILD_PREVIEW, children.size()))) {
            lines.add(child(kit, child));
        }
        int more = children.size() - CHILD_PREVIEW;
        if (more > 0) {
            lines.add(kit.text("      … и ещё " + more + " подзадач, откройте задачу", ChatPalette.FAINT));
        }
        return lines;
    }


    private List<Component> listFooter(ChatKit kit, TaskPeriod period, TaskState state, int page, int pages) {
        List<Component> lines = new ArrayList<Component>();
        if (pages > 1) {
            lines.add(kit.blank());
            lines.add(ChatPager.pager(kit, ROOT + " " + period.getWord() + " " + state.getWord(), page, pages));
        }
        if (!kit.getReader().isInteractive()) {
            lines.add(kit.blank());
            lines.addAll(kit.commandHints(
                    new CommandHint(ROOT + " [today|week|older|any] [all|active|failed|done] [страница]", "список задач"),
                    new CommandHint(ROOT + " view <id>", "карточка задачи"),
                    new CommandHint(ROOT + " run <id>", "запустить сейчас"),
                    new CommandHint(ROOT + " cancel <id>", "остановить задачу")));
        }
        return lines;
    }


    private <T> Component tabs(ChatKit kit, String title, List<T> choices, T current, Function<T, String> label,
            ToIntFunction<T> count, Function<T, String> command) {
        Component line = kit.text(" " + title + ": ", ChatPalette.LABEL);
        boolean first = true;
        for (T choice : choices) {
            if (!first) {
                line = line.append(kit.text(" "));
            }
            first = false;
            String text = label.apply(choice) + " " + count.applyAsInt(choice);
            line = line.append(choice.equals(current)
                    ? kit.selected(text, ChatPalette.ACCENT)
                    : kit.option(text, command.apply(choice), "Показать: " + label.apply(choice), ChatPalette.SOFT));
        }
        return line;
    }


    private Component row(ChatKit kit, TaskSnapshot task, List<TaskSnapshot> all) {
        Component title = kit.text(" ").append(kit.text(kit.glyph('●'), TaskLabels.color(task.getStatus())))
                .append(kit.text(" " + TaskLabels.title(task), ChatPalette.ACCENT))
                .append(kit.text("  " + TaskLabels.label(task.getStatus()), TaskLabels.color(task.getStatus())));
        Component block = title.append(Component.newline()).append(facts(kit, task, all));
        if (task.getDescription() != null) {
            String preview = task.getDescription();
            block = block.append(Component.newline())
                    .append(kit.text("    " + preview.substring(0, Math.min(DESCRIPTION_PREVIEW, preview.length())), ChatPalette.LABEL));
        }
        if (!kit.getReader().isInteractive()) {
            return block;
        }
        return block.hoverEvent(kit.tooltip(hover(kit, task).toArray(new Component[0])))
                .clickEvent(ClickEvent.runCommand(ROOT + " view " + task.getId().getValue()));
    }


    private Component facts(ChatKit kit, TaskSnapshot task, List<TaskSnapshot> all) {
        String runs = task.getRunCount() + " " + RussianText.plural(task.getRunCount(), "запуск", "запуска", "запусков");
        Component facts = kit.text("    " + task.getOwnerName(), ChatPalette.TEXT)
                .append(kit.dot()).append(kit.text(runs, ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(TaskLabels.ago(TaskLabels.activity(task)), ChatPalette.FAINT));
        if (task.getLastFailure() != null) {
            facts = facts.append(kit.dot()).append(kit.text("ошибка", ChatPalette.DANGER));
        }
        String parent = TaskLabels.parent(task, all);
        if (parent != null) {
            facts = facts.append(kit.dot()).append(kit.text("↳ " + parent, ChatPalette.SOFT));
        }
        if (TaskLabels.lag(task) != null) {
            facts = facts.append(kit.dot()).append(kit.text("лаг", ChatPalette.DANGER));
        }
        return facts;
    }


    private List<Component> hover(ChatKit kit, TaskSnapshot task) {
        List<Component> hover = new ArrayList<Component>();
        hover.add(kit.text(TaskLabels.title(task), ChatPalette.ACCENT));
        if (task.getDescription() != null) {
            hover.add(kit.text(task.getDescription(), ChatPalette.TEXT));
        }
        hover.add(kit.blank());
        hover.add(kit.hoverField("Состояние", kit.text(TaskLabels.label(task.getStatus()), TaskLabels.color(task.getStatus()))));
        hover.add(kit.hoverField("Плагин", kit.text(task.getOwnerName(), ChatPalette.TEXT)));
        hover.add(kit.hoverField("Последнее", kit.text(TaskLabels.moment(TaskLabels.activity(task)), ChatPalette.TEXT)));
        hover.add(kit.blank());
        hover.add(kit.text("Нажмите, чтобы открыть задачу ›", ChatPalette.ACCENT));
        return hover;
    }


    private Component child(ChatKit kit, TaskSnapshot task) {
        Component line = kit.text("    ↳ ", ChatPalette.FAINT).append(kit.text(kit.glyph('●'), TaskLabels.color(task.getStatus())))
                .append(kit.text(" " + TaskLabels.title(task), ChatPalette.TEXT))
                .append(kit.text("  " + TaskLabels.label(task.getStatus()), TaskLabels.color(task.getStatus())));
        if (TaskLabels.lag(task) != null) {
            line = line.append(kit.dot()).append(kit.text("лаг", ChatPalette.DANGER));
        }
        if (!kit.getReader().isInteractive()) {
            return line;
        }
        return line.clickEvent(ClickEvent.runCommand(ROOT + " view " + task.getId().getValue()));
    }
}
