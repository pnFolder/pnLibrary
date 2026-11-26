package org.pnfolder.pnlibrary.core.commands.tasks;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.common.time.DisplayZone;

/** Chat wording, colours and time formats for task statuses and moments. */
public final class TaskLabels {
    public static final int SHORT_ID = 8;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long HOURS_PER_DAY = 24L;
    private static final Duration NOTABLE_LAG = Duration.ofSeconds(1);

    private TaskLabels() {
    }

    @NotNull
    public static String label(@NotNull TaskStatus status) {
        switch (status) {
            case SCHEDULED:
                return "ожидает запуска";
            case RUNNING:
                return "выполняется";
            case COMPLETED:
                return "завершена";
            case CANCELLED:
                return "отменена";
            default:
                return "упала";
        }
    }

    @NotNull
    public static TextColor color(@NotNull TaskStatus status) {
        switch (status) {
            case SCHEDULED:
                return ChatPalette.INFO;
            case RUNNING:
                return ChatPalette.OK;
            case COMPLETED:
                return ChatPalette.SOFT;
            case CANCELLED:
                return ChatPalette.FAINT;
            default:
                return ChatPalette.DANGER;
        }
    }

    /** The latest moment something happened to the task: it started, completed, or was created. */
    @NotNull
    public static Instant activity(@NotNull TaskSnapshot task) {
        Instant latest = task.getCreatedAt();
        for (Instant moment : new Instant[] {task.getLastStartedAt(), task.getLastCompletedAt()}) {
            if (moment != null && moment.isAfter(latest)) {
                latest = moment;
            }
        }
        return latest;
    }

    public static boolean isToday(@NotNull TaskSnapshot task) {
        ZoneId zone = DisplayZone.getZone();
        return activity(task).atZone(zone).toLocalDate().equals(LocalDate.now(zone));
    }

    @NotNull
    public static String title(@NotNull TaskSnapshot task) {
        if (task.getName() != null) {
            return task.getName();
        }
        if (task.getKey() != null) {
            return task.getKey();
        }
        String id = task.getId().getValue();
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }

    /** Name of the task that started {@code task}, or {@code null} for a task scheduled directly. */
    @Nullable
    public static String parent(@NotNull TaskSnapshot task, @NotNull Collection<TaskSnapshot> all) {
        TaskId parentId = task.getParentId();
        if (parentId == null) {
            return null;
        }
        for (TaskSnapshot candidate : all) {
            if (candidate.getId().equals(parentId)) {
                return title(candidate);
            }
        }
        return "уже завершена";
    }

    /** Tasks with no parent in {@code all}, the top level of the list. */
    @NotNull
    public static List<TaskSnapshot> roots(@NotNull List<TaskSnapshot> all) {
        Set<TaskId> ids = new HashSet<>();
        for (TaskSnapshot task : all) {
            ids.add(task.getId());
        }
        List<TaskSnapshot> roots = new ArrayList<>();
        for (TaskSnapshot task : all) {
            if (task.getParentId() == null || !ids.contains(task.getParentId())) {
                roots.add(task);
            }
        }
        return roots;
    }

    /** Tasks started by {@code task}. */
    @NotNull
    public static List<TaskSnapshot> childrenOf(@NotNull TaskSnapshot task, @NotNull Collection<TaskSnapshot> all) {
        List<TaskSnapshot> children = new ArrayList<>();
        for (TaskSnapshot candidate : all) {
            if (task.getId().equals(candidate.getParentId())) {
                children.add(candidate);
            }
        }
        return children;
    }

    /** {@code 3, активных 1} for the subtasks of {@code task}. */
    @NotNull
    public static String childrenLine(@NotNull TaskSnapshot task, @NotNull Collection<TaskSnapshot> all) {
        List<TaskSnapshot> children = childrenOf(task, all);
        int active = 0;
        for (TaskSnapshot child : children) {
            if (child.isActive()) {
                active++;
            }
        }
        return children.size() + ", активных " + active;
    }

    /** How many tasks were started by {@code task}. */
    public static int children(@NotNull TaskSnapshot task, @NotNull Collection<TaskSnapshot> all) {
        return childrenOf(task, all).size();
    }

    /** Text about a noticeable delay such as a server freeze, or {@code null} when the task ran on time. */
    @Nullable
    public static String lag(@NotNull TaskSnapshot task) {
        Duration worst = task.getWorstLag();
        if (worst == null || worst.compareTo(NOTABLE_LAG) < 0) {
            return null;
        }
        Duration last = task.getLastLag() == null ? Duration.ZERO : task.getLastLag();
        return "последнее " + span(last) + ", наибольшее " + span(worst);
    }

    @NotNull
    public static String stopAnswer(@NotNull TaskSnapshot task) {
        if (!task.isActive()) {
            return "нет, задача уже " + label(task.getStatus());
        }
        return task.getCancellable() ? "да" : "нет, плагин запретил";
    }

    @NotNull
    public static String runAnswer(@NotNull TaskSnapshot task) {
        if (!task.getRunnableNow()) {
            return "нет, плагин запретил";
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            return "нет, задача выполняется";
        }
        if (task.getStatus() != TaskStatus.SCHEDULED) {
            return "нет, задача уже " + label(task.getStatus());
        }
        return "да";
    }

    @NotNull
    public static String where(@NotNull TaskSnapshot task) {
        switch (task.getExecutionKind().name()) {
            case "ASYNC":
                return "отдельный поток, мир не трогать";
            case "ENTITY":
                return "поток существа";
            case "REGION":
                return "поток региона";
            default:
                return "основной поток сервера";
        }
    }

    /** A moment in the configured time zone: {@code 29 октября, 14:05:09}, with the year when it is not this year. */
    @NotNull
    public static String moment(@NotNull Instant at) {
        ZoneId zone = DisplayZone.getZone();
        String year = at.atZone(zone).getYear() == LocalDate.now(zone).getYear() ? "" : " yyyy";
        return DateTimeFormatter.ofPattern("d MMMM" + year + ", HH:mm:ss", new Locale("ru")).withZone(zone).format(at);
    }

    /** How long ago {@code at} was, or how long until it: {@code только что}, {@code 5 мин назад}, {@code через 2 ч}. */
    @NotNull
    public static String ago(@NotNull Instant at) {
        Duration gap = Duration.between(at, Instant.now());
        if (gap.abs().getSeconds() < SECONDS_PER_MINUTE) {
            return "только что";
        }
        return gap.isNegative() ? "через " + span(gap.negated()) : span(gap) + " назад";
    }

    @NotNull
    public static String span(@NotNull Duration value) {
        long millis = value.toMillis();
        if (millis < MILLIS_PER_SECOND) {
            return millis + " мс";
        }
        if (millis < MILLIS_PER_SECOND * SECONDS_PER_MINUTE) {
            return millis / MILLIS_PER_SECOND + " с";
        }
        if (value.toHours() < 1) {
            return value.toMinutes() + " мин";
        }
        if (value.toDays() < 1) {
            return (value.toHours() + " ч " + value.toMinutes() % SECONDS_PER_MINUTE + " мин").replace(" 0 мин", "");
        }
        return (value.toDays() + " д " + value.toHours() % HOURS_PER_DAY + " ч").replace(" 0 ч", "");
    }
}
