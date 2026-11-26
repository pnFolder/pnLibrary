package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** The numbers the library counts about itself: {@code {pnlibrary.stats.errors}} and the rest. */
public final class LibraryStatistics {
    private final Supplier<Map<String, Long>> statistics;

    public LibraryStatistics(@NotNull Supplier<Map<String, Long>> statistics) {
        this.statistics = statistics;
    }

    private long count(String key) {
        Long value = statistics.get().get(key);
        return value == null ? 0L : value;
    }

    @Placeholder("Errors since start")
    public Reply<Long> errors() {
        return Reply.of(count("errors"));
    }

    @Placeholder("Warnings since start")
    public Reply<Long> warnings() {
        return Reply.of(count("warnings"));
    }

    @Placeholder("Tasks run since start")
    public Reply<Long> tasks() {
        return Reply.of(count("tasks"));
    }

    @Placeholder("Tasks that failed")
    public Reply<Long> failedTasks() {
        return Reply.of(count("tasks.failed"));
    }

    @Placeholder("Events handled")
    public Reply<Long> events() {
        return Reply.of(count("events"));
    }
}
