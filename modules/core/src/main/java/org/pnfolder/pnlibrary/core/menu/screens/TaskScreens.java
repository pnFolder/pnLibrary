package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.data.EventAbout;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Menu screens for scheduled tasks. */
public final class TaskScreens extends MenuScreens {
    private final TaskDetailScreens details;

    public TaskScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.details = new TaskDetailScreens(session, routes);
    }

    public void tasks(MenuViewer player, TaskPeriod period, TaskState state, int page) {
        TaskPeriod nextPeriod = TaskPeriod.values()[(period.ordinal() + 1) % TaskPeriod.values().length];
        TaskState nextState = TaskState.values()[(state.ordinal() + 1) % TaskState.values().length];
        List<TaskSnapshot> everything = data().tasks();
        List<TaskSnapshot> all = TaskLabels.roots(everything);
        List<TaskSnapshot> inPeriod = new ArrayList<>();
        for (TaskSnapshot task : all) {
            if (period.keep(task)) {
                inPeriod.add(task);
            }
        }
        List<TaskSnapshot> shown = new ArrayList<>();
        for (TaskSnapshot task : inPeriod) {
            if (state.keep(task)) {
                shown.add(task);
            }
        }
        Collections.sort(shown, new Comparator<TaskSnapshot>() {
            @Override
            public int compare(TaskSnapshot a, TaskSnapshot b) {
                return TaskLabels.activity(b).compareTo(TaskLabels.activity(a));
            }
        });
        List<Row> rows = new ArrayList<>();
        for (TaskSnapshot task : shown) {
            rows.add(new Row(TaskItems.item(task, everything, routes),
                go(who -> details.detail(who, task.getId().getValue(), v -> tasks(v, period, state, page)))));
        }
        List<String> periods = new ArrayList<>();
        for (TaskPeriod each : TaskPeriod.values()) {
            periods.add(each.getLabel() + " " + count(all, each, null));
        }
        List<String> states = new ArrayList<>();
        for (TaskState each : TaskState.values()) {
            states.add(each.getLabel() + " " + count(inPeriod, null, each));
        }
        ItemSpec byDate = MenuItems.icon(Icons.CLOCK, "CLOCK", "Когда", Style.lore(
            Collections.singletonList("Задачи за какое время показывать."), Collections.<Fact>emptyList(),
            Style.choices(periods, period.ordinal()), Style.left("переключить")), "WATCH");
        ItemSpec byState = MenuItems.icon(Icons.SETTINGS, "HOPPER", "Какие", Style.lore(
            Collections.singletonList("Какие задачи показывать."), Collections.<Fact>emptyList(),
            Style.choices(states, state.ordinal()), Style.left("переключить")));
        list(player, "Задачи: " + period.getLabel() + ", " + state.getLabel(), rows, page, MenuConstants.PAGE_SIZE,
            (who, to) -> tasks(who, period, state, to), routes::home, "Задач нет", builder -> {
                builder.button(MenuConstants.DATE_FILTER, byDate, go(who -> tasks(who, nextPeriod, state, 0)));
                builder.button(MenuConstants.FILTER, byState, go(who -> tasks(who, period, nextState, 0)));
            });
    }

    private static int count(List<TaskSnapshot> tasks, @Nullable TaskPeriod period, @Nullable TaskState state) {
        int count = 0;
        for (TaskSnapshot task : tasks) {
            if (period != null ? period.keep(task) : state.keep(task)) {
                count++;
            }
        }
        return count;
    }

    /** What the event called {@code name} says about itself, or null when it is unknown. */
    public @Nullable EventAbout aboutOf(String name) {
        return data().eventAbout(name);
    }

    /** {@code Name 1.2.3} for a plugin, or the plain name when the server does not know it. */
    public String ownerLine(String name) {
        OwnerInfo owner = data().ownerNamed(name);
        return owner == null ? name : versioned(owner);
    }

    public String versioned(OwnerInfo owner) {
        return owner.getVersion() == null ? owner.getName() : owner.getName() + " " + owner.getVersion();
    }

    public List<Fact> ownerParameters(@Nullable OwnerInfo owner) {
        List<Fact> facts = new ArrayList<>();
        if (owner == null) {
            facts.add(new Fact("Плагин", "сервер или библиотека"));
            return facts;
        }
        facts.add(new Fact("Плагин", owner.getName()));
        if (owner.getVersion() != null) {
            facts.add(new Fact("Версия", owner.getVersion()));
        }
        if (!owner.getAuthors().isEmpty()) {
            facts.add(new Fact("Автор", String.join(", ", owner.getAuthors())));
        }
        return facts;
    }

    /** The text as a label followed by indented lines, for a value that does not fit one line. */
    public List<Fact> labelled(String label, String text) {
        List<Fact> facts = new ArrayList<>();
        List<String> lines = CoreFormatting.wrapText(text);
        for (int index = 0; index < lines.size(); index++) {
            facts.add(new Fact(index == 0 ? label : "", lines.get(index)));
        }
        return facts;
    }
}
