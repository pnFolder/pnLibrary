package org.pnfolder.pnlibrary.core.menu.screens.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

/** The card of one task, its subtasks and the buttons that run or stop it. */
final class TaskDetailScreens extends MenuScreens {
    private final TaskFactScreens facts;

    TaskDetailScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.facts = new TaskFactScreens(routes);
    }

    void detail(MenuViewer player, String id, Consumer<MenuViewer> back) {
        TaskSnapshot task = find(id);
        if (task == null) {
            tell(player, ChatTone.INFO, "Эта задача уже исчезла из списка.");
            back.accept(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Задача")).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        facts.summary(builder, task, data().tasks());
        facts.facts(builder, task);
        relations(builder, task, back);
        runControl(builder, task, back);
        stopControl(builder, task, back);
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private TaskSnapshot find(String id) {
        for (TaskSnapshot task : data().tasks()) {
            if (task.getId().getValue().equals(id)) {
                return task;
            }
        }
        return null;
    }

    /** Slot 33: the parent of a subtask, or the list of subtasks of a task that started some. */
    private void relations(MenuBuilder builder, TaskSnapshot task, Consumer<MenuViewer> back) {
        List<TaskSnapshot> all = data().tasks();
        TaskSnapshot parent = null;
        for (TaskSnapshot candidate : all) {
            if (candidate.getId().equals(task.getParentId())) {
                parent = candidate;
            }
        }
        if (parent != null) {
            String parentId = parent.getId().getValue();
            builder.button(33, MenuItems.icon(Icons.ROLLBACK, "ARROW", "Подзадача от: " + TaskLabels.title(parent), Style.lore(
                Collections.singletonList("Эту задачу запустила другая задача."),
                Fact.list("Состояние", TaskLabels.label(parent.getStatus())), Style.left("открыть родителя"))),
                go(who -> detail(who, parentId, back)));
        }
        List<TaskSnapshot> children = TaskLabels.childrenOf(task, all);
        if (!children.isEmpty()) {
            int active = 0;
            for (TaskSnapshot child : children) {
                active += child.isActive() ? 1 : 0;
            }
            builder.button(34, MenuItems.icon(Icons.CHANGELOG, "CHEST", "Подзадачи: " + children.size(), Style.lore(
                Collections.singletonList("Задачи, которые запустила эта задача."),
                Fact.list("Активных", String.valueOf(active)), Style.left("показать"))),
                go(who -> subtasks(who, task.getId().getValue(), back)));
        }
    }

    private void subtasks(MenuViewer player, String parentId, Consumer<MenuViewer> back) {
        List<TaskSnapshot> all = data().tasks();
        TaskSnapshot parent = null;
        for (TaskSnapshot task : all) {
            if (task.getId().getValue().equals(parentId)) {
                parent = task;
            }
        }
        if (parent == null) {
            back.accept(player);
            return;
        }
        List<TaskSnapshot> children = new ArrayList<>(TaskLabels.childrenOf(parent, all));
        Collections.sort(children, (a, b) -> TaskLabels.activity(b).compareTo(TaskLabels.activity(a)));
        List<Row> rows = new ArrayList<>();
        for (TaskSnapshot task : children) {
            rows.add(new Row(TaskItems.item(task, all, routes),
                go(who -> detail(who, task.getId().getValue(), v -> subtasks(v, parentId, back)))));
        }
        list(player, "Подзадачи: " + TaskLabels.title(parent), rows, 0, MenuConstants.PAGE_SIZE,
            (who, to) -> subtasks(who, parentId, back), who -> detail(who, parentId, back), "Подзадач нет");
    }

    private void runControl(MenuBuilder builder, TaskSnapshot task, Consumer<MenuViewer> back) {
        if (!(task.getStatus() == TaskStatus.SCHEDULED && task.getRunnableNow())) {
            builder.slot(29, closed("Запуск закрыт", capital(TaskLabels.runAnswer(task)) + "."));
            return;
        }
        ItemSpec run = MenuItems.icon(Icons.CHECK, "EMERALD", "Запустить сейчас", Style.lore(
            Collections.singletonList("Выполнить один раз, не дожидаясь срока. Расписание не меняется."),
            Collections.emptyList(), Style.left("запустить")), Tone.GOOD);
        builder.button(29, run, go(who -> allowed(who, MenuConstants.TASKS, () -> runNow(who, task, back))));
    }

    private void runNow(MenuViewer who, TaskSnapshot task, Consumer<MenuViewer> back) {
        TaskControl result = data().runTask(task.getId().getValue());
        if (result == TaskControl.DONE) {
            tell(who, ChatTone.SUCCESS, "Задача запущена.");
        } else if (result == TaskControl.FORBIDDEN) {
            tell(who, ChatTone.FAILURE, "Плагин не разрешает запускать эту задачу вручную.");
        } else {
            tell(who, ChatTone.INFO, "Сейчас запустить нельзя: задача не ждёт срока.");
        }
        detail(who, task.getId().getValue(), back);
    }

    private void stopControl(MenuBuilder builder, TaskSnapshot task, Consumer<MenuViewer> back) {
        if (!task.isActive()) {
            builder.slot(31, closed("Остановка закрыта", capital(TaskLabels.stopAnswer(task)) + "."));
            return;
        }
        if (!task.getCancellable()) {
            builder.slot(31, closed("Остановка закрыта", "Плагин запретил останавливать эту задачу из меню и команд."));
            return;
        }
        ItemSpec stop = MenuItems.icon(Icons.ROLLBACK, "TNT", "Остановить задачу", Style.lore(
            Collections.singletonList("Больше не будет запускаться."), Collections.emptyList(), Style.left("остановить")),
            Tone.DANGER);
        builder.button(31, stop, go(who -> allowed(who, MenuConstants.TASKS, () -> stopNow(who, task, back))));
    }

    private void stopNow(MenuViewer who, TaskSnapshot task, Consumer<MenuViewer> back) {
        TaskControl result = data().cancelTask(task.getId().getValue());
        if (result == TaskControl.DONE) {
            tell(who, ChatTone.SUCCESS, "Задача остановлена.");
        } else if (result == TaskControl.FORBIDDEN) {
            tell(who, ChatTone.FAILURE, "Плагин не разрешает останавливать эту задачу.");
        } else {
            tell(who, ChatTone.INFO, "Задача уже не работает.");
        }
        detail(who, task.getId().getValue(), back);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static ItemSpec closed(String name, String reason) {
        return MenuItems.icon(Icons.PROBLEMS, "BARRIER", name,
            Style.lore(Collections.singletonList(reason), Collections.emptyList()), Tone.MUTED);
    }
}
