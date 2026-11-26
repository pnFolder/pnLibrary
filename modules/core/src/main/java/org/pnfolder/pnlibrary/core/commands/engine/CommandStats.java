package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/** Counters of what the commands did: runs, suggestions and failures, in total and by command name. */
final class CommandStats {
    private final AtomicLong executionCount = new AtomicLong();
    private final AtomicLong suggestionCount = new AtomicLong();
    private final AtomicLong executionFailureCount = new AtomicLong();
    private final AtomicLong suggestionFailureCount = new AtomicLong();
    private final Object lock = new Object();
    private final Map<String, Long> executionsByCommand = new LinkedHashMap<>();
    private final Map<String, Long> failuresByCommand = new LinkedHashMap<>();

    void executed(String commandName) {
        executionCount.incrementAndGet();
        synchronized (lock) {
            increment(executionsByCommand, commandName);
        }
    }

    void executionFailed(String commandName) {
        executionFailureCount.incrementAndGet();
        synchronized (lock) {
            increment(failuresByCommand, commandName);
        }
    }

    void suggested() {
        suggestionCount.incrementAndGet();
    }

    void suggestionFailed() {
        suggestionFailureCount.incrementAndGet();
    }

    private static void increment(Map<String, Long> counts, String name) {
        Long before = counts.get(name);
        counts.put(name, (before == null ? 0L : before) + 1L);
    }

    Map<String, Object> snapshot(List<RegisteredCommand> open) {
        TreeSet<String> names = new TreeSet<>();
        for (RegisteredCommand registration : open) {
            names.addAll(registration.getCommand().getAliases());
            names.add(registration.getCommand().getName());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registeredCount", open.size());
        result.put("registeredNames", Collections.unmodifiableList(new ArrayList<>(names)));
        result.put("executionCount", executionCount.get());
        result.put("suggestionCount", suggestionCount.get());
        result.put("executionFailureCount", executionFailureCount.get());
        result.put("suggestionFailureCount", suggestionFailureCount.get());
        synchronized (lock) {
            result.put("executionsByCommand", new TreeMap<>(executionsByCommand));
            result.put("failuresByCommand", new TreeMap<>(failuresByCommand));
        }
        return result;
    }
}
