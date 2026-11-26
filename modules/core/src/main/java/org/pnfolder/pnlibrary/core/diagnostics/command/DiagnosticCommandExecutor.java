package org.pnfolder.pnlibrary.core.diagnostics.command;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/**
 * Runs the platform-independent {@code /pn debug} command flow.
 *
 * <p>Platform adapters remain responsible for permissions, native command registration, and message rendering.
 * Parsing, cooldown, background report creation, and safe reply dispatch are implemented once here.
 */
public final class DiagnosticCommandExecutor {
    private final PnLibrary library;
    private final Clock clock;
    private final Map<String, Long> lastRequestTimes = new ConcurrentHashMap<String, Long>();

    /** Creates an executor that uses {@code library} for report generation and task dispatch. */
    public DiagnosticCommandExecutor(@NotNull PnLibrary library) {
        this(library, Clock.systemUTC());
    }

    /** Creates an executor whose user-facing cooldown reads time from {@code clock}. */
    public DiagnosticCommandExecutor(@NotNull PnLibrary library, @NotNull Clock clock) {
        this.library = library;
        this.clock = clock;
    }

    /**
     * Parses and submits one diagnostic command.
     *
     * <p>Immediate events are published on the caller thread. Completion events are dispatched through the public
     * task service to the {@code recipient} context.
     */
    public void execute(@NotNull String[] arguments, boolean prefixed, @NotNull String requesterId,
                        @NotNull Object recipient, @NotNull Consumer<DiagnosticCommandEvent> publish) {
        DebugRequest request = parse(arguments, prefixed);
        if (request == null) {
            publish.accept(DiagnosticCommandEvent.InvalidUsage.INSTANCE);
            return;
        }
        long remaining = startCooldown(requesterId);
        if (remaining > 0) {
            publish.accept(new DiagnosticCommandEvent.CoolingDown(remaining));
            return;
        }
        publish.accept(new DiagnosticCommandEvent.Started(request.getTarget()));
        TaskScope tasks = RuntimeBridge.runtimeTaskScope(library);
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.async())
            .action(context -> reply(tasks, request, recipient, publish))
            .build());
    }

    private static DebugRequest parse(String[] arguments, boolean prefixed) {
        try {
            return DebugRequest.parse(arguments, prefixed);
        } catch (IllegalArgumentException invalid) {
            // Bad arguments are an expected user mistake and answered with the usage line.
            return null;
        }
    }

    private void reply(TaskScope tasks, DebugRequest request, Object recipient,
                       Consumer<DiagnosticCommandEvent> publish) {
        DiagnosticCommandEvent event;
        try {
            event = new DiagnosticCommandEvent.Completed(library.createDiagnosticReport(request));
        } catch (Exception exception) {
            String message = exception.getMessage();
            event = new DiagnosticCommandEvent.Failed(message != null ? message : exception.getClass().getSimpleName());
        }
        DiagnosticCommandEvent result = event;
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.entity(recipient))
            .action(context -> publish.accept(result))
            .build());
    }

    /**
     * Starts the requester's cooldown and returns 0, or returns the whole seconds still left of it. Check and
     * start are one atomic step, so two quick requests cannot both pass.
     */
    private long startCooldown(String requesterId) {
        final long cooldownMillis = library.getConfiguration().getCooldown().toMillis();
        final long now = clock.millis();
        final long[] remaining = {0L};
        lastRequestTimes.compute(requesterId, (id, last) -> {
            long elapsed = last == null ? Long.MAX_VALUE : now - last;
            if (cooldownMillis == 0L || elapsed >= cooldownMillis) {
                return now;
            }
            remaining[0] = (cooldownMillis - elapsed + 999L) / 1_000L;
            return last;
        });
        return remaining[0];
    }
}
