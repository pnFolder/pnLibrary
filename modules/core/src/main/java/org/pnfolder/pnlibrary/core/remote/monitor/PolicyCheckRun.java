package org.pnfolder.pnlibrary.core.remote.monitor;

import org.pnfolder.pnlibrary.core.remote.report.RemotePolicyNoticeRenderer;
import org.pnfolder.pnlibrary.core.remote.report.PolicyFailureReport;
import org.pnfolder.pnlibrary.core.remote.engine.RemotePolicyEngine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.result.DenyAction;
import org.pnfolder.pnlibrary.api.remote.result.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnerControl;

/** One scheduled policy check: evaluates the policy off-thread, then acts on the result on the global thread. */
final class PolicyCheckRun implements Runnable {
    private final RemotePolicyEngine engine;
    private final PlatformOwnerControl ownership;
    private final PlatformExecution execution;
    private final RemotePolicyNoticeRenderer notices;
    private final Object owner;
    private final PluginMetadata metadata;
    private final RemotePolicySettings policy;
    private final RemotePolicyContext context;
    private final AtomicBoolean checked;
    private final BooleanSupplier isClosed;
    private final PnLogger logger;
    private final Runnable closeModule;
    private final Runnable closePlugin;

    PolicyCheckRun(RemotePolicyEngine engine, PlatformOwnerControl ownership, PlatformExecution execution,
                   RemotePolicyNoticeRenderer notices, Object owner, PluginMetadata metadata,
                   RemotePolicySettings policy, RemotePolicyContext context, AtomicBoolean checked,
                   BooleanSupplier isClosed, PnLogger logger, Runnable closeModule, Runnable closePlugin) {
        this.engine = engine;
        this.ownership = ownership;
        this.execution = execution;
        this.notices = notices;
        this.owner = owner;
        this.metadata = metadata;
        this.policy = policy;
        this.context = context;
        this.checked = checked;
        this.isClosed = isClosed;
        this.logger = logger;
        this.closeModule = closeModule;
        this.closePlugin = closePlugin;
    }

    @Override
    public void run() {
        if (isClosed.getAsBoolean()) {
            return;
        }
        Attempt<RemotePolicyResult> attempt = Failures.runCatchingNonFatal(() -> engine.check(policy.getSource(), context));
        if (attempt.isSuccess()) {
            RemotePolicyResult decision = attempt.getOrNull();
            execution.executeGlobal(() -> onDecision(decision));
        } else {
            Throwable error = attempt.exceptionOrNull();
            execution.executeGlobal(() -> onFailure(error));
        }
    }

    private void onDecision(RemotePolicyResult decision) {
        if (isClosed.getAsBoolean()) {
            return;
        }
        notices.render(owner, metadata, policy, decision);
        if (decision.getAllowed()) {
            checked.set(true);
        } else {
            deny();
        }
    }

    private void onFailure(Throwable error) {
        if (isClosed.getAsBoolean()) {
            return;
        }
        boolean keepRunning = policy.getOnError() == PolicyErrorAction.KEEP_RUNNING
            || policy.getOnError() == PolicyErrorAction.DENY_UNTIL_CHECKED && checked.get();
        if (keepRunning) {
            PolicyFailureReport.unchecked(notices, owner, metadata, policy, error);
            return;
        }
        PolicyFailureReport.deny(notices, owner, metadata, policy, logger, error);
        deny();
    }

    private void deny() {
        if (policy.getOnDeny() == DenyAction.DISABLE_MODULE) {
            closeModule.run();
        } else if (!ownership.disableOwner(owner)) {
            closePlugin.run();
        }
    }
}
