package org.pnfolder.pnlibrary.api.events.inspect;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Read-only description of one registered listener, returned by {@link EventInspector#listeners()}.
 *
 * <pre>{@code
 * for (ListenerInfo info : inspector.listeners(PurchaseEvent.class)) {
 *     System.out.println(info.getPlugin() + "/" + info.getModule() + " " + info.getPriority() + " " + info.getSource());
 * }
 * }</pre>
 */
public final class ListenerInfo {
    private final PluginId plugin;
    private final ModuleId module;
    private final String eventType;
    private final int priority;
    private final boolean ignoreDenied;
    private final String source;
    private final long invocations;
    private final long failures;
    private final long totalNanos;
    private final long maxNanos;

    /** Description of a listener of a release that did not measure time. */
    public ListenerInfo(
            @NotNull PluginId plugin,
            @Nullable ModuleId module,
            @NotNull String eventType,
            int priority,
            boolean ignoreDenied,
            @NotNull String source,
            long invocations,
            long failures) {
        this(plugin, module, eventType, priority, ignoreDenied, source, invocations, failures, 0, 0);
    }

    /**
     * Description of one listener.
     *
     * @param plugin plugin that registered the listener
     * @param module module of that plugin, or {@code null} when registered directly on the bus
     * @param eventType name of the class the listener subscribed to; it also receives subclasses
     * @param priority priority the listener was registered with; smaller values run first
     * @param ignoreDenied whether the listener is skipped once the event is denied
     * @param source {@code Class#method} for an {@code @EventHandler} method, or the listener class for a lambda
     * @param invocations how many times the listener has run since it was registered
     * @param failures how many of those runs threw an exception
     * @param totalNanos time all runs took together, in nanoseconds; {@code 0} when the bus does not measure
     * @param maxNanos the slowest single run, in nanoseconds
     */
    public ListenerInfo(
            @NotNull PluginId plugin,
            @Nullable ModuleId module,
            @NotNull String eventType,
            int priority,
            boolean ignoreDenied,
            @NotNull String source,
            long invocations,
            long failures,
            long totalNanos,
            long maxNanos) {
        this.plugin = plugin;
        this.module = module;
        this.eventType = eventType;
        this.priority = priority;
        this.ignoreDenied = ignoreDenied;
        this.source = source;
        this.invocations = invocations;
        this.failures = failures;
        this.totalNanos = totalNanos;
        this.maxNanos = maxNanos;
    }

    /** Plugin that registered the listener. */
    @NotNull
    public PluginId getPlugin() {
        return plugin;
    }

    /** Module of that plugin that registered it, or {@code null} when registered directly on the bus. */
    @Nullable
    public ModuleId getModule() {
        return module;
    }

    /** Name of the class the listener subscribed to; it also receives subclasses. */
    @NotNull
    public String getEventType() {
        return eventType;
    }

    /** Priority the listener was registered with; smaller values run first. */
    public int getPriority() {
        return priority;
    }

    /** Whether the listener is skipped once the event is denied. */
    public boolean getIgnoreDenied() {
        return ignoreDenied;
    }

    /** {@code Class#method} for an {@code @EventHandler} method, or the listener class for a lambda. */
    @NotNull
    public String getSource() {
        return source;
    }

    /** How many times the listener has run since it was registered. */
    public long getInvocations() {
        return invocations;
    }

    /** How many of those runs threw an exception. */
    public long getFailures() {
        return failures;
    }

    /** Time all runs took together, in nanoseconds; {@code 0} when the bus does not measure. */
    public long getTotalNanos() {
        return totalNanos;
    }

    /** The slowest single run, in nanoseconds. */
    public long getMaxNanos() {
        return maxNanos;
    }

    /** Mean time of one run in nanoseconds, or {@code 0} when the listener has not run. */
    public long getAverageNanos() {
        return invocations > 0 ? totalNanos / invocations : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ListenerInfo)) {
            return false;
        }
        ListenerInfo that = (ListenerInfo) other;
        return priority == that.priority
                && ignoreDenied == that.ignoreDenied
                && invocations == that.invocations
                && failures == that.failures
                && totalNanos == that.totalNanos
                && maxNanos == that.maxNanos
                && plugin.equals(that.plugin)
                && Objects.equals(module, that.module)
                && eventType.equals(that.eventType)
                && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, module, eventType, priority, ignoreDenied, source, invocations, failures, totalNanos, maxNanos);
    }

    @Override
    public String toString() {
        return "ListenerInfo(plugin=" + plugin + ", module=" + module + ", eventType=" + eventType + ", priority=" + priority
                + ", source=" + source + ", invocations=" + invocations + ", failures=" + failures + ")";
    }
}
