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
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerControl
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Periodically evaluates a remote policy and stops the module or plugin when the policy denies it. */
internal class RemotePolicyMonitor(
    private val ownership: PlatformOwnerControl,
    private val execution: PlatformExecution,
    private val notices: RemotePolicyNoticeRenderer,
) {
    /** Runs startup checks; daemon threads so a hung download never keeps the JVM alive, and an idle one is dropped. */
    private val startupChecks = Executors.newCachedThreadPool { task ->
        Thread(task, "pnlibrary-policy-check").apply { isDaemon = true }
    }

    fun start(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicySettings,
        tasks: TaskScope,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
        startupCheck: StartupCheck? = null,
    ) {
        val policyContext = ownership.remotePolicyContext(owner, policy.values)
        val checked = AtomicBoolean(startupCheck == StartupCheck.PASSED)
        tasks.schedule(
            TaskSpec.builder()
                .name("remote policy: ${metadata.id.value}")
                .description("Проверка безопасности.")
                // Stopping the check would let a banned plugin keep running.
                .cancellable(false)
                .runnableNow(false)
                .execution(TaskExecution.async())
                // A check already made at start is not repeated at once.
                .apply { if (startupCheck != null) delay(policy.checkEvery) }
                .interval(policy.checkEvery)
                .action {
                    if (!isClosed()) check(owner, metadata, policy, policyContext, checked, isClosed, logger, closeModule, closePlugin)
                }
                .build(),
        )
    }

    /** How the check made while the plugin was starting ended. */
    enum class StartupCheck {
        /** The policy allowed the plugin. */
        PASSED,

        /** The policy could not be checked, and [PolicyErrorAction.KEEP_RUNNING] lets the plugin start anyway. */
        UNCHECKED,
    }

    /**
     * Evaluates the policy once on the calling thread, before the plugin reports itself enabled, and prints the card.
     * Returns how it ended, or `null` when the plugin must not start (denied, or unchecked and the policy says deny).
     * The wait is bounded, so an unreachable host cannot hold the server start for long.
     */
    fun checkAtStartup(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicySettings,
        logger: PnLogger,
        timeout: Duration,
    ): StartupCheck? {
        val context = ownership.remotePolicyContext(owner, policy.values)
        val outcome = startupChecks.submit(Callable { RemotePolicyEngine.check(policy.source, context) })
        return try {
            val decision = outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            notices.render(owner, metadata, policy, decision)
            if (decision.allowed) StartupCheck.PASSED else null
        } catch (failure: Throwable) {
            // A check that outlived its wait must not keep compiling or downloading in the background.
            outcome.cancel(true)
            val error = when (failure) {
                is TimeoutException -> SocketTimeoutException("Проверка не уложилась в ${timeout.seconds} с")
                is ExecutionException -> failure.cause ?: failure
                else -> failure
            }
            if (policy.onError == PolicyErrorAction.KEEP_RUNNING) {
                notices.unchecked(
                    owner, metadata, policy,
                    RemotePolicyFailureInterpreter.message(error), RemotePolicyFailureInterpreter.isNetwork(error),
                )
                StartupCheck.UNCHECKED
            } else {
                val explanation = RemotePolicyExplanation.builder("Ошибка проверки")
                    .child(RemotePolicyFailureInterpreter.message(error))
                if (RemotePolicyFailureInterpreter.isNetwork(error)) {
                    explanation.child("Для запуска нужен интернет: откройте серверу доступ к ${hostOf(policy.source)}")
                }
                notices.render(owner, metadata, policy, false, explanation.build())
                logger.error("Remote policy failed for ${metadata.name}", error)
                null
            }
        }
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
        runCatchingNonFatal { RemotePolicyEngine.check(policy.source, policyContext) }
            .onSuccess { decision ->
                execution.executeGlobal(Runnable {
                    if (isClosed()) return@Runnable
                    notices.render(owner, metadata, policy, decision)
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
                        explanation.child("Для запуска нужен интернет: откройте серверу доступ к ${hostOf(policy.source)}")
                    }
                    notices.render(owner, metadata, policy, false, explanation.build())
                    logger.error("Remote policy failed for ${metadata.name}", error)
                    deny(owner, policy, closeModule, closePlugin)
                })
            }
    }

    private fun hostOf(source: String): String =
        runCatchingNonFatal { URI(source).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "хосту политики"

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
