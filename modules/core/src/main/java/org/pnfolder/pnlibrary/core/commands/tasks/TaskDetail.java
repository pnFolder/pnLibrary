package org.pnfolder.pnlibrary.core.commands.tasks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.common.time.DisplayZone;

/** Chat page with the details of one task. */
final class TaskDetail {
    private TaskDetail() {
    }

    static List<Component> who(ChatKit kit, TaskSnapshot task, Collection<TaskSnapshot> all) {
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.section("Кто и как"));
        lines.add(kit.field("Плагин", kit.text(task.getOwnerName(), ChatPalette.TEXT)));
        String parent = TaskLabels.parent(task, all);
        if (parent != null) {
            lines.add(kit.field("Подзадача от", kit.text(parent, ChatPalette.TEXT)));
        }
        int children = TaskLabels.children(task, all);
        if (children > 0) {
            lines.add(kit.field("Подзадач", kit.text(String.valueOf(children), ChatPalette.TEXT)));
        }
        identity(kit, task, lines);
        lines.add(kit.field("Выполняется", kit.text(TaskLabels.where(task), ChatPalette.TEXT)));
        if (task.getInterval() != null) {
            lines.add(kit.field("Повторяется", kit.text("каждые " + TaskLabels.span(task.getInterval()), ChatPalette.TEXT)));
        }
        lines.add(kit.field("Запусков", kit.text(task.getRunCount() + ", пропущено " + task.getSkippedCount(), ChatPalette.TEXT)));
        if (task.getLastDuration() != null) {
            lines.add(kit.field("Прошлый запуск шёл", kit.text(TaskLabels.span(task.getLastDuration()), ChatPalette.TEXT)));
        }
        String lag = TaskLabels.lag(task);
        if (lag != null) {
            lines.add(kit.field("Опоздание", kit.text(lag, ChatPalette.DANGER)));
        }
        return lines;
    }

    static List<Component> who(ChatKit kit, TaskSnapshot task) {
        return who(kit, task, Collections.emptyList());
    }

    private static void identity(ChatKit kit, TaskSnapshot task, List<Component> lines) {
        if (task.getSource() != null) {
            lines.add(kit.field("Класс", kit.text(task.getSource(), ChatPalette.TEXT)));
        }
        if (task.getKey() != null) {
            lines.add(kit.field("Ключ", kit.text(task.getKey(), ChatPalette.TEXT)));
        }
        if (!task.getTags().isEmpty()) {
            lines.add(kit.field("Теги", kit.text(String.join(", ", task.getTags()), ChatPalette.SOFT)));
        }
    }

    static List<Component> whenLines(ChatKit kit, TaskSnapshot task) {
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.section("Когда"));
        lines.add(kit.field("Создана", moment(kit, task.getCreatedAt())));
        if (task.getNextRunAt() != null) {
            lines.add(kit.field("Следующий запуск", moment(kit, task.getNextRunAt())));
        }
        if (task.getLastStartedAt() != null) {
            lines.add(kit.field("Последний запуск", moment(kit, task.getLastStartedAt())));
        }
        if (task.getLastCompletedAt() != null) {
            lines.add(kit.field("Закончилась", moment(kit, task.getLastCompletedAt())));
        }
        lines.add(kit.field("Часовой пояс", kit.text(DisplayZone.getZone().getId(), ChatPalette.FAINT)));
        return lines;
    }

    static List<Component> subtasks(ChatKit kit, TaskSnapshot task, Collection<TaskSnapshot> all) {
        TaskSnapshot parent = null;
        for (TaskSnapshot candidate : all) {
            if (candidate.getId().equals(task.getParentId())) {
                parent = candidate;
                break;
            }
        }
        List<TaskSnapshot> children = TaskLabels.childrenOf(task, all);
        if (parent == null && children.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.section("Связанные задачи"));
        if (parent != null) {
            lines.add(kit.field("Родитель", kit.text(TaskLabels.title(parent), ChatPalette.ACCENT).clickEvent(open(parent))));
        }
        for (TaskSnapshot child : children) {
            String text = TaskLabels.title(child) + ", " + TaskLabels.label(child.getStatus());
            lines.add(kit.field("Подзадача", kit.text(text, ChatPalette.TEXT).clickEvent(open(child))));
        }
        return lines;
    }

    private static ClickEvent open(TaskSnapshot task) {
        return ClickEvent.runCommand("/pn tasks view " + task.getId().getValue());
    }

    static List<Component> rights(ChatKit kit, TaskSnapshot task) {
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.section("Права"));
        lines.add(kit.field("Остановить", answer(kit, TaskLabels.stopAnswer(task))));
        lines.add(kit.field("Запустить сейчас", answer(kit, TaskLabels.runAnswer(task))));
        return lines;
    }

    private static Component moment(ChatKit kit, Instant at) {
        return kit.text(TaskLabels.moment(at), ChatPalette.TEXT).append(kit.text("  " + TaskLabels.ago(at), ChatPalette.FAINT));
    }

    private static Component answer(ChatKit kit, @NotNull String text) {
        return kit.text(text, text.equals("да") ? ChatPalette.OK : ChatPalette.SOFT);
    }
}
