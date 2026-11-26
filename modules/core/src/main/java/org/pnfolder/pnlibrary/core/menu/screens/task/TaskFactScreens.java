package org.pnfolder.pnlibrary.core.menu.screens.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** The information slots of a task card: summary, how it runs, delays, times and origin. */
final class TaskFactScreens {
    private final MenuRoutes routes;

    TaskFactScreens(MenuRoutes routes) {
        this.routes = routes;
    }

    void summary(MenuBuilder builder, TaskSnapshot task, List<TaskSnapshot> all) {
        List<Fact> summary = new ArrayList<>();
        summary.add(new Fact("Плагин", routes.ownerLine(task.getOwnerName())));
        summary.add(new Fact("Состояние", TaskLabels.label(task.getStatus())));
        if (task.getInterval() != null) {
            summary.add(new Fact("Повторяется", "каждые " + TaskLabels.span(task.getInterval())));
        }
        String parent = TaskLabels.parent(task, all);
        if (parent != null) {
            summary.add(new Fact("Подзадача от", parent));
        }
        int children = TaskLabels.children(task, all);
        if (children > 0) {
            summary.add(new Fact("Подзадач", String.valueOf(children)));
        }
        List<String> about = task.getDescription() == null
            ? Collections.singletonList("Плагин не описал эту задачу.") : CoreFormatting.wrapText(task.getDescription());
        builder.slot(4, MenuItems.icon(null, "CLOCK", TaskLabels.title(task), Style.lore(about, summary), "WATCH",
            Tone.of(task.getStatus() == TaskStatus.FAILED, false, task.getStatus() == TaskStatus.RUNNING, false)));
    }

    void facts(MenuBuilder builder, TaskSnapshot task) {
        List<String> usage = task.getUsage() == null
            ? Collections.singletonList("Не указано. Плагин может добавить usage в TaskSpec.") : CoreFormatting.wrapText(task.getUsage());
        builder.slot(20, MenuItems.icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Как используется",
            Style.lore(usage, Collections.emptyList()), task.getUsage() == null ? Tone.MUTED : Tone.NORMAL));
        List<Fact> runs = Fact.list("Запусков", String.valueOf(task.getRunCount()), "Пропущено", String.valueOf(task.getSkippedCount()));
        if (task.getLastDuration() != null) {
            runs.add(new Fact("Прошлый запуск шёл", TaskLabels.span(task.getLastDuration())));
        }
        if (TaskLabels.lag(task) != null) {
            runs.add(new Fact("Опоздание", TaskLabels.lag(task)));
        }
        runs.add(new Fact("Где", TaskLabels.where(task)));
        builder.slot(21, MenuItems.icon(Icons.SETTINGS, "COMPARATOR", "Как выполняется",
            Style.lore(Collections.emptyList(), runs)));
        lagSlot(builder, task);
        times(builder, task);
    }

    private void lagSlot(MenuBuilder builder, TaskSnapshot task) {
        String lag = TaskLabels.lag(task);
        List<Fact> lore = new ArrayList<>();
        if (task.getLastLag() != null) {
            lore.add(new Fact("Последнее", TaskLabels.span(task.getLastLag())));
        }
        if (task.getWorstLag() != null) {
            lore.add(new Fact("Наибольшее", TaskLabels.span(task.getWorstLag())));
        }
        lore.add(new Fact("Пропущено", String.valueOf(task.getSkippedCount())));
        if (task.getFailedCount() > 0) {
            lore.add(new Fact("Ошибок", String.valueOf(task.getFailedCount())));
        }
        String note = lag == null ? "Запуски идут вовремя." : "Сервер или поток не успевали: запуск начинался позже срока.";
        builder.slot(25, MenuItems.icon(Icons.PROBLEMS, "CLOCK", "Опоздания",
            Style.lore(Collections.singletonList(note), lore), "WATCH", Tone.of(lag != null, false, false, lag == null)));
    }

    private void times(MenuBuilder builder, TaskSnapshot task) {
        List<Fact> times = new ArrayList<>();
        times.add(new Fact("Создана", MenuText.clockStamp(task.getCreatedAt())));
        if (task.getNextRunAt() != null) {
            times.add(new Fact("Следующий запуск", MenuText.clockStamp(task.getNextRunAt())));
            times.add(new Fact("", MenuText.ago(task.getNextRunAt())));
        }
        if (task.getLastStartedAt() != null) {
            times.add(new Fact("Последний запуск", MenuText.clockStamp(task.getLastStartedAt())));
            times.add(new Fact("", MenuText.ago(task.getLastStartedAt())));
        }
        if (task.getLastCompletedAt() != null) {
            times.add(new Fact("Закончилась", MenuText.clockStamp(task.getLastCompletedAt())));
        }
        times.add(new Fact("Пояс", DisplayZone.getZone().getId()));
        builder.slot(22, MenuItems.icon(Icons.CLOCK, "CLOCK", "Время", Style.lore(Collections.emptyList(), times), "WATCH"));
        List<Fact> origin = new ArrayList<>();
        if (task.getSource() != null) {
            origin.addAll(routes.labelled("Класс", task.getSource()));
        }
        if (task.getKey() != null) {
            origin.add(new Fact("Ключ", task.getKey()));
        }
        if (!task.getTags().isEmpty()) {
            origin.addAll(routes.labelled("Теги", String.join(", ", task.getTags())));
        }
        origin.add(new Fact("Остановить", TaskLabels.stopAnswer(task)));
        origin.add(new Fact("Запустить сейчас", TaskLabels.runAnswer(task)));
        String id = task.getId().getValue();
        origin.add(new Fact("Номер", id.length() > TaskLabels.SHORT_ID ? id.substring(0, TaskLabels.SHORT_ID) : id));
        builder.slot(23, MenuItems.icon(Icons.PLUGIN, "PAPER", "Откуда и права", Style.lore(Collections.emptyList(), origin)));
        if (task.getLastFailure() != null) {
            List<String> danger = new ArrayList<>();
            for (String line : CoreFormatting.wrapText(task.getLastFailure())) {
                danger.add(Style.danger(line));
            }
            builder.slot(24, MenuItems.icon(Icons.PROBLEMS, "BARRIER", "Последняя ошибка",
                Style.lore(Collections.emptyList(), Collections.emptyList(), danger), Tone.DANGER));
        }
    }
}
