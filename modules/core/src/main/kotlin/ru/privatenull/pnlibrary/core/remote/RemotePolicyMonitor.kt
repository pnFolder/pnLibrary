package ru.privatenull.pnlibrary.core.remote

import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnerControl

/** Periodically evaluates a remote policy and stops the module or plugin when the policy denies it. */
internal class RemotePolicyMonitor(
    private val ownership: PlatformOwnerControl,
    private val execution: PlatformExecution,
    private val notices: RemotePolicyNoticeRenderer,
) {
    fun start(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicy,
        tasks: TaskScope,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    ) {
        val policyContext = ownership.remotePolicyContext(owner, policy.values)
        tasks.schedule(
            TaskSpec.builder()
                .name("remote policy: ${metadata.id.value}")
                .execution(TaskExecution.async())
                .interval(policy.checkEvery)
                .action {
                    if (!isClosed()) check(owner, metadata, policy, policyContext, isClosed, logger, closeModule, closePlugin)
                }
                .build(),
        )
    }

    private fun check(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicy,
        policyContext: RemotePolicyContext,
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
                    if (!decision.allowed) deny(owner, policy, closeModule, closePlugin)
                })
            }
            .onFailure { error ->
                execution.executeGlobal(Runnable {
                    if (isClosed()) return@Runnable
                    val explanation = RemotePolicyExplanation.builder("Ошибка проверки")
                        .child(RemotePolicyFailureInterpreter.message(error))
                        .build()
                    notices.render(owner, metadata, policy, false, explanation)
                    logger.error("Remote policy failed for ${metadata.name}", error)
                    deny(owner, policy, closeModule, closePlugin)
                })
            }
    }

    private fun deny(
        owner: Any,
        policy: RemotePolicy,
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
