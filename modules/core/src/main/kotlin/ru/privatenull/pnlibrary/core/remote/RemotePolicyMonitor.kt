package ru.privatenull.pnlibrary.core.remote

import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

internal class RemotePolicyMonitor(
    private val platform: PlatformAdapter,
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
        val policyContext = platform.remotePolicyContext(owner, metadata, policy.values)
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
        policyContext: ru.privatenull.pnlibrary.api.remote.RemotePolicyContext,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    ) {
        runCatching { RemotePolicyEngine.check(policy.source, policyContext) }
            .onSuccess { decision ->
                platform.executeGlobal(Runnable {
                    if (isClosed()) return@Runnable
                    notices.render(owner, metadata, policy, decision.allowed, decision.explanation)
                    if (!decision.allowed) deny(owner, policy, closeModule, closePlugin)
                })
            }
            .onFailure { error ->
                platform.executeGlobal(Runnable {
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
        } else if (!platform.disableOwner(owner)) {
            closePlugin()
        }
    }
}
