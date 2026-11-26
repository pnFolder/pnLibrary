package org.pnfolder.pnlibrary.core.commands.tasks;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/** The card of one task in {@code /pn tasks view}: who runs it, when, what may be done to it and the last failure. */
final class TaskCard {
    private static final String ROOT = "/pn tasks";

    private final PnLibrary library;

    TaskCard(PnLibrary library) {
        this.library = library;
    }

    List<Component> lines(ChatKit kit, TaskSnapshot task) {
        List<TaskSnapshot> all = RuntimeBridge.taskSnapshots(library);
        List<Component> lines = new ArrayList<Component>();
        lines.add(kit.blank());
        lines.add(kit.header(TaskLabels.title(task),
                kit.bracketed(TaskLabels.label(task.getStatus()), TaskLabels.color(task.getStatus()))));
        if (task.getDescription() != null) {
            lines.add(kit.note(task.getDescription(), ChatPalette.TEXT));
        }
        if (task.getUsage() != null) {
            lines.add(kit.note("Как используется: " + task.getUsage(), ChatPalette.LABEL));
        }
        if (task.getDescription() == null && task.getUsage() == null) {
            lines.add(kit.note("Плагин не описал эту задачу. Описание задаётся в TaskSpec: description и usage.", ChatPalette.FAINT));
        }
        lines.addAll(TaskDetail.who(kit, task, all));
        lines.addAll(TaskDetail.whenLines(kit, task));
        lines.addAll(TaskDetail.rights(kit, task));
        lines.addAll(TaskDetail.subtasks(kit, task, all));
        failure(kit, task, lines);
        lines.add(kit.blank());
        Component actions = kit.actions(kit.button("назад", ROOT, "К списку задач", ChatPalette.ACCENT), start(kit, task), stop(kit, task));
        if (actions != null) {
            lines.add(actions);
        }
        return lines;
    }


    private void failure(ChatKit kit, TaskSnapshot task, List<Component> lines) {
        if (task.getLastFailure() == null) {
            return;
        }
        lines.add(kit.blank());
        lines.add(kit.section("Ошибка"));
        lines.add(kit.note(task.getLastFailure(), ChatPalette.DANGER));
    }


    private Component start(ChatKit kit, TaskSnapshot task) {
        if (task.getStatus() == TaskStatus.SCHEDULED && task.getRunnableNow()) {
            return kit.button("запустить сейчас", ROOT + " run " + task.getId().getValue(), "Выполнить, не дожидаясь срока",
                    ChatPalette.OK);
        }
        return closed(kit, "запуск закрыт", TaskLabels.runAnswer(task));
    }


    private Component stop(ChatKit kit, TaskSnapshot task) {
        if (task.isActive() && task.getCancellable()) {
            return kit.button("остановить", ROOT + " cancel " + task.getId().getValue(), "Остановить задачу", ChatPalette.DANGER);
        }
        return closed(kit, "остановка закрыта", TaskLabels.stopAnswer(task));
    }


    private Component closed(ChatKit kit, String label, String reason) {
        if (!kit.getReader().isInteractive()) {
            return null;
        }
        String capital = reason.isEmpty() ? reason : reason.substring(0, 1).toUpperCase() + reason.substring(1);
        return kit.bracketed(label, ChatPalette.FAINT).hoverEvent(kit.tooltip(kit.text(capital, ChatPalette.SOFT)));
    }
}
