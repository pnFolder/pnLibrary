package org.pnfolder.pnlibrary.core.remote

import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.DenyAction
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerControl
import java.util.concurrent.atomic.AtomicBoolean

/** Periodically evaluates a remote policy and stops the module or plugin when the policy denies it. */
internal class RemotePolicyMonitor(
    private val ownership: PlatformOwnerControl,
    private val execution: PlatformExecution,
    private val notices: RemotePolicyNoticeRenderer,
) {
    fun start(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicySettings,
        tasks: TaskScope,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    ) {
        val policyContext = ownership.remotePolicyContext(owner, policy.values)
        val checked = AtomicBoolean()
        tasks.schedule(
            TaskSpec.builder()
                .name("remote policy: ${metadata.id.value}")
                .execution(TaskExecution.async())
                .interval(policy.checkEvery)
                .action {
                    if (!isClosed()) check(owner, metadata, policy, policyContext, checked, isClosed, logger, closeModule, closePlugin)
                }
                .build(),
        )
    }

    private fun check(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicySettings,
        policyContext: RemotePolicyContext,
        checked: AtomicBoolean,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    ) {
        runCatching { RemotePolicyEngine.check(policy.source, policyContext) }
            .onSuccess { decision ->
                execution.executeGlobal(Runnable {
                    if (isClosed()) return@Runnable
                    notices.render(owner, metadata, policy, decision.allowed, decision.explanation)
                    if (decision.allowed) checked.set(true) else deny(owner, policy, closeModule, closePlugin)
                })
            }
            .onFailure { error ->
                execution.executeGlobal(Runnable {
                    if (isClosed()) return@Runnable
                    val keepRunning = policy.onError == PolicyErrorAction.KEEP_RUNNING ||
                        policy.onError == PolicyErrorAction.DENY_UNTIL_CHECKED && checked.get()
                    if (keepRunning) {
                        notices.unchecked(
                            owner, metadata, policy,
                            RemotePolicyFailureInterpreter.message(error),
                            RemotePolicyFailureInterpreter.isNetwork(error),
                        )
                        return@Runnable
                    }
                    val explanation = RemotePolicyExplanation.builder("Ошибка проверки")
                        .child(RemotePolicyFailureInterpreter.message(error))
                    if (RemotePolicyFailureInterpreter.isNetwork(error)) {
                        explanation.child("Для запуска нужен интернет: откройте серверу доступ к raw.githubusercontent.com")
                    }
                    notices.render(owner, metadata, policy, false, explanation.build())
                    logger.error("Remote policy failed for ${metadata.name}", error)
                    deny(owner, policy, closeModule, closePlugin)
                })
            }
    }

    private fun deny(
        owner: Any,
        policy: RemotePolicySettings,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    ) {
        if (policy.onDeny == DenyAction.DISABLE_MODULE) {
            closeModule()
        } else if (!ownership.disableOwner(owner)) {
            closePlugin()
        }
    }
}
