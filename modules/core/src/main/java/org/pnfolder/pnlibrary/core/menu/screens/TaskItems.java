package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** The button of one task in a list. */
final class TaskItems {
    private TaskItems() {
    }

    static ItemSpec item(TaskSnapshot task, Collection<TaskSnapshot> all, MenuRoutes routes) {
        String material;
        switch (task.getStatus()) {
            case SCHEDULED:
                material = "CLOCK";
                break;
            case RUNNING:
                material = "EMERALD";
                break;
            case COMPLETED:
                material = "PAPER";
                break;
            case CANCELLED:
                material = "COAL";
                break;
            default:
                material = "BARRIER";
                break;
        }
        List<String> description = task.getDescription() == null
            ? Collections.singletonList("Плагин не описал эту задачу.")
            : CoreFormatting.wrapText(task.getDescription(), CoreFormatting.WRAP_WIDTH, 3);
        List<Fact> parameters = new ArrayList<>();
        parameters.add(new Fact("Плагин", routes.ownerLine(task.getOwnerName())));
        parameters.add(new Fact("Состояние", TaskLabels.label(task.getStatus())));
        parameters.add(new Fact("Запусков", String.valueOf(task.getRunCount())));
        if (TaskLabels.children(task, all) > 0) {
            parameters.add(new Fact("Подзадач", TaskLabels.childrenLine(task, all)));
        }
        String parent = TaskLabels.parent(task, all);
        if (parent != null) {
            parameters.add(new Fact("Подзадача от", parent));
        }
        String lag = TaskLabels.lag(task);
        if (lag != null) {
            parameters.add(new Fact("Опоздание", lag));
        }
        String failure = task.getLastFailure();
        if (failure != null) {
            parameters.add(new Fact("Ошибка", "✖ " + (failure.length() > MenuConstants.MESSAGE_PREVIEW
                ? failure.substring(0, MenuConstants.MESSAGE_PREVIEW) : failure)));
        }
        return MenuItems.icon(null, material, TaskLabels.title(task),
            Style.lore(description, parameters, Style.left("подробности")), material.equals("CLOCK") ? "WATCH" : null,
            Tone.of(task.getStatus() == TaskStatus.FAILED, false, task.getStatus() == TaskStatus.RUNNING,
                task.getStatus() == TaskStatus.CANCELLED));
    }
}
