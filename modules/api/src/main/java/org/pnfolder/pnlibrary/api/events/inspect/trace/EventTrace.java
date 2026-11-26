package org.pnfolder.pnlibrary.api.events.inspect.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * One finished dispatch of an event, recorded while tracing is enabled for its type.
 *
 * <pre>{@code
 * inspector.enableTracing(PurchaseEvent.class);
 * new PurchaseEvent(player, 1500).callEvent();
 * for (EventTrace trace : inspector.traces()) {
 *     trace.getSteps().forEach(System.out::println);
 * }
 * }</pre>
 */
public final class EventTrace {
    private final String eventName;
    private final boolean allowed;
    private final String result;
    private final List<Step> steps;
    private final DispatchMode mode;
    private final String thread;
    private final Instant at;

    /** A synchronous trace without thread name and time. */
    public EventTrace(@NotNull String eventName, boolean allowed, @Nullable String result, @NotNull List<Step> steps) {
        this(eventName, allowed, result, steps, DispatchMode.SYNC, "", null);
    }

    /**
     * One recorded dispatch.
     *
     * @param eventName {@code Event.getEventName()} of the dispatched event
     * @param allowed {@code Event.isAllowed()} once every listener had run
     * @param result {@code toString()} of the final result, or {@code null} for events without a result
     * @param steps one entry per matching listener, in the order they were considered
     * @param mode how the event was dispatched
     * @param thread name of the thread the listeners ran on
     * @param at when the dispatch finished, or {@code null} for a trace recorded without a time
     */
    public EventTrace(
            @NotNull String eventName,
            boolean allowed,
            @Nullable String result,
            @NotNull List<Step> steps,
            @NotNull DispatchMode mode,
            @NotNull String thread,
            @Nullable Instant at) {
        this.eventName = Objects.requireNonNull(eventName, "eventName");
        this.allowed = allowed;
        this.result = result;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.mode = mode;
        this.thread = thread;
        this.at = at;
    }

    /** Name of the dispatched event. */
    @NotNull
    public String getEventName() {
        return eventName;
    }

    /** Whether the event was allowed once every listener had run. */
    public boolean getAllowed() {
        return allowed;
    }

    /** {@code toString()} of the final result, or {@code null} for events without a result. */
    @Nullable
    public String getResult() {
        return result;
    }

    /** One entry per matching listener, in the order they were considered. */
    @NotNull
    public List<Step> getSteps() {
        return steps;
    }

    /** How the event was dispatched. */
    @NotNull
    public DispatchMode getMode() {
        return mode;
    }

    /** Name of the thread the listeners ran on. */
    @NotNull
    public String getThread() {
        return thread;
    }

    /** When the dispatch finished, or {@code null} for a trace recorded without a time. */
    @Nullable
    public Instant getAt() {
        return at;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventTrace)) {
            return false;
        }
        EventTrace that = (EventTrace) other;
        return allowed == that.allowed
                && eventName.equals(that.eventName)
                && Objects.equals(result, that.result)
                && steps.equals(that.steps)
                && mode == that.mode
                && thread.equals(that.thread)
                && Objects.equals(at, that.at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventName, allowed, result, steps, mode, thread, at);
    }

    @Override
    public String toString() {
        return "EventTrace(eventName=" + eventName + ", allowed=" + allowed + ", result=" + result + ", steps=" + steps
                + ", mode=" + mode + ", thread=" + thread + ", at=" + at + ")";
    }

    /** What happened to one listener during a single dispatch. */
    public static final class Step {
        private final PluginId plugin;
        private final ModuleId module;
        private final int priority;
        private final boolean skipped;
        private final boolean failed;
        private final long nanos;
        private final String resultBefore;
        private final String resultAfter;

        /**
         * One listener of a dispatch.
         *
         * @param plugin plugin that registered the listener
         * @param module module of that plugin, or {@code null} for a listener registered directly on the bus
         * @param priority priority the listener was registered with
         * @param skipped {@code true} when it did not run because the event was already denied and it asked to ignore
         *     denied events
         * @param failed {@code true} when the listener threw; the error is logged and dispatch continues
         * @param nanos time spent inside the listener, {@code 0} when it was skipped
         * @param resultBefore {@code toString()} of the result before the listener ran, or {@code null} without a result
         * @param resultAfter {@code toString()} of the result after the listener ran, or {@code null} without a result
         */
        public Step(
                @NotNull PluginId plugin,
                @Nullable ModuleId module,
                int priority,
                boolean skipped,
                boolean failed,
                long nanos,
                @Nullable String resultBefore,
                @Nullable String resultAfter) {
            this.plugin = plugin;
            this.module = module;
            this.priority = priority;
            this.skipped = skipped;
            this.failed = failed;
            this.nanos = nanos;
            this.resultBefore = resultBefore;
            this.resultAfter = resultAfter;
        }

        /** Plugin that registered the listener. */
        @NotNull
        public PluginId getPlugin() {
            return plugin;
        }

        /** Module of that plugin, or {@code null} for a listener registered directly on the bus. */
        @Nullable
        public ModuleId getModule() {
            return module;
        }

        /** Priority the listener was registered with. */
        public int getPriority() {
            return priority;
        }

        /** Whether the listener was skipped because the event was already denied. */
        public boolean getSkipped() {
            return skipped;
        }

        /** Whether the listener threw. */
        public boolean getFailed() {
            return failed;
        }

        /** Time spent inside the listener, {@code 0} when it was skipped. */
        public long getNanos() {
            return nanos;
        }

        /** {@code toString()} of the result before the listener ran, or {@code null} without a result. */
        @Nullable
        public String getResultBefore() {
            return resultBefore;
        }

        /** {@code toString()} of the result after the listener ran, or {@code null} without a result. */
        @Nullable
        public String getResultAfter() {
            return resultAfter;
        }

        /** Whether the listener replaced the result, judged by comparing the result before and after. */
        public boolean getChangedResult() {
            return !Objects.equals(resultBefore, resultAfter);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Step)) {
                return false;
            }
            Step that = (Step) other;
            return priority == that.priority
                    && skipped == that.skipped
                    && failed == that.failed
                    && nanos == that.nanos
                    && plugin.equals(that.plugin)
                    && Objects.equals(module, that.module)
                    && Objects.equals(resultBefore, that.resultBefore)
                    && Objects.equals(resultAfter, that.resultAfter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(plugin, module, priority, skipped, failed, nanos, resultBefore, resultAfter);
        }

        @Override
        public String toString() {
            return "Step(plugin=" + plugin + ", module=" + module + ", priority=" + priority + ", skipped=" + skipped
                    + ", failed=" + failed + ", nanos=" + nanos + ")";
        }
    }
}
