package org.pnfolder.pnlibrary.core.remote.monitor;

import org.pnfolder.pnlibrary.core.remote.report.RemotePolicyNoticeRenderer;
import org.pnfolder.pnlibrary.core.remote.report.PolicyFailureReport;
import org.pnfolder.pnlibrary.core.remote.engine.PolicyCompilers;
import org.pnfolder.pnlibrary.core.remote.engine.RemotePolicyEngine;
import java.util.Objects;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.result.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnerControl;

/** Periodically evaluates a remote policy and stops the module or plugin when the policy denies it. */
public final class RemotePolicyMonitor {
    private final PlatformOwnerControl ownership;
    private final PlatformExecution execution;
    private final RemotePolicyNoticeRenderer notices;
    private final RemotePolicyEngine engine = new RemotePolicyEngine(new PolicyCompilers());

    /** Runs startup checks; daemon threads so a hung download never keeps the JVM alive, and an idle one is dropped. */
    private final ExecutorService startupChecks = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "pnlibrary-policy-check");
        thread.setDaemon(true);
        return thread;
    });

    public RemotePolicyMonitor(@NotNull PlatformOwnerControl ownership, @NotNull PlatformExecution execution,
                               @NotNull RemotePolicyNoticeRenderer notices) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    /** Starts the policy of {@code module}; a denial closes the module, or the whole plugin through {@code closePlugin}. */
    public void start(@NotNull Object owner, @NotNull ModuleContext module, @NotNull RemotePolicySettings policy,
                      @NotNull Runnable closePlugin) {
        start(owner, module.getMetadata(), policy, module.getTasks(), module::isClosed, module.getLogger(),
            module::close, closePlugin, null);
    }

    /** Starts the periodic check; {@code startupCheck} is what {@link #checkAtStartup} returned, or {@code null}. */
    public void start(@NotNull Object owner, @NotNull PluginMetadata metadata, @NotNull RemotePolicySettings policy,
                      @NotNull TaskScope tasks, @NotNull BooleanSupplier isClosed, @NotNull PnLogger logger,
                      @NotNull Runnable closeModule, @NotNull Runnable closePlugin, @Nullable StartupCheck startupCheck) {
        RemotePolicyContext context = ownership.remotePolicyContext(owner, policy.getValues());
        AtomicBoolean checked = new AtomicBoolean(startupCheck == StartupCheck.PASSED);
        PolicyCheckRun run = new PolicyCheckRun(engine, ownership, execution, notices, owner, metadata, policy,
            context, checked, isClosed, logger, closeModule, closePlugin);
        TaskSpec.Builder spec = TaskSpec.builder()
            .name("remote policy: " + metadata.getId().getValue())
            .description("Проверка безопасности.")
            // Stopping the check would let a banned plugin keep running.
            .cancellable(false)
            .runnableNow(false)
            .execution(TaskExecution.async());
        // A check already made at start is not repeated at once.
        if (startupCheck != null) {
            spec.delay(policy.getCheckEvery());
        }
        tasks.schedule(spec.interval(policy.getCheckEvery()).action(task -> run.run()).build());
    }

    /**
     * Evaluates the policy once on the calling thread, before the plugin reports itself enabled, and prints the card.
     * Returns how it ended, or {@code null} when the plugin must not start (denied, or unchecked and the policy says
     * deny). The wait is bounded, so an unreachable host cannot hold the server start for long.
     */
    public @Nullable StartupCheck checkAtStartup(@NotNull Object owner, @NotNull PluginMetadata metadata,
                                                 @NotNull RemotePolicySettings policy, @NotNull PnLogger logger,
                                                 @NotNull Duration timeout) {
        RemotePolicyContext context = ownership.remotePolicyContext(owner, policy.getValues());
        Future<RemotePolicyResult> outcome = startupChecks.submit(() -> engine.check(policy.getSource(), context));
        try {
            RemotePolicyResult decision = outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            notices.render(owner, metadata, policy, decision);
            return decision.getAllowed() ? StartupCheck.PASSED : null;
        } catch (Throwable failure) {
            // A check that outlived its wait must not keep compiling or downloading in the background.
            outcome.cancel(true);
            return onStartupFailure(owner, metadata, policy, logger, rootOf(failure, timeout));
        }
    }

    private StartupCheck onStartupFailure(Object owner, PluginMetadata metadata, RemotePolicySettings policy,
                                          PnLogger logger, Throwable error) {
        if (policy.getOnError() == PolicyErrorAction.KEEP_RUNNING) {
            PolicyFailureReport.unchecked(notices, owner, metadata, policy, error);
            return StartupCheck.UNCHECKED;
        }
        PolicyFailureReport.deny(notices, owner, metadata, policy, logger, error);
        return null;
    }

    private static Throwable rootOf(Throwable failure, Duration timeout) {
        if (failure instanceof TimeoutException) {
            return new SocketTimeoutException("Проверка не уложилась в " + timeout.getSeconds() + " с");
        }
        if (failure instanceof ExecutionException) {
            return failure.getCause() != null ? failure.getCause() : failure;
        }
        return failure;
    }
}
