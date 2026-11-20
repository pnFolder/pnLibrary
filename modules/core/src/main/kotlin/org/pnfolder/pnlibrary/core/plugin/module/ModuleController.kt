package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.common.lang.Failures
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Runs the hooks of one [PluginModule] and keeps its state. Changes run one at a time; a hook that throws leaves the
 * module [ModuleState.FAILED] and the error is kept instead of being thrown at the caller.
 *
 * @param release takes back what the library gave the module while it ran (tasks, commands)
 */
internal class ModuleController(
    private val key: String,
    private val module: PluginModule?,
    private val preferences: ModulePreferences,
    private val release: () -> Unit,
) : ModuleControl {
    private lateinit var context: ModuleContext

    @Volatile override var state: ModuleState = ModuleState.ENABLED
        private set
    @Volatile override var lastError: Throwable? = null
        private set

    override val isControllable: Boolean get() = module != null
    override val autoStart: Boolean get() = preferences.autoStart(key)

    fun bind(context: ModuleContext) {
        this.context = context
    }

    /** First start at registration: stays off when an administrator turned the module off for good. */
    fun start() {
        if (module == null) return
        if (!autoStart) state = ModuleState.DISABLED else runEnable()
    }

    /** Last stop when the module or its plugin goes away; errors are kept, never thrown. */
    fun stop() {
        if (module != null && state == ModuleState.ENABLED) runDisable()
    }

    override fun enable(): CompletionStage<ModuleState> = change { if (state != ModuleState.ENABLED) runEnable() }

    override fun disable(): CompletionStage<ModuleState> = change {
        if (state == ModuleState.ENABLED) runDisable() else if (state == ModuleState.FAILED) {
            release()
            state = ModuleState.DISABLED
        }
    }

    override fun restart(): CompletionStage<ModuleState> = change {
        if (state == ModuleState.ENABLED) runDisable()
        runEnable()
    }

    override fun reload(): CompletionStage<ModuleState> = change {
        if (state == ModuleState.ENABLED) {
            val inPlace = try {
                module!!.onReload(context)
            } catch (failure: Throwable) {
                Failures.rethrowIfFatal(failure)
                error(failure)
                return@change
            }
            if (!inPlace) {
                runDisable()
                runEnable()
            }
        }
    }

    override fun setAutoStart(enabled: Boolean) {
        requireControllable()
        preferences.setAutoStart(key, enabled)
    }

    private fun change(action: () -> Unit): CompletionStage<ModuleState> {
        requireControllable()
        synchronized(this) {
            lastError = null
            action()
        }
        return CompletableFuture.completedFuture(state)
    }

    private fun requireControllable() {
        if (module == null) throw UnsupportedOperationException("This module has no enable and disable hooks")
    }

    private fun runEnable() = guarded(ModuleState.ENABLED) {
        for (library in module!!.libraries()) {
            try {
                context.libraries.load(library)
            } catch (failure: Exception) {
                if (library.required) throw failure
                context.logger.warning("Optional library $library was not loaded: ${failure.message}")
            }
        }
        module.onEnable(context)
    }

    private fun runDisable() = guarded(ModuleState.DISABLED) {
        try {
            module!!.onDisable(context)
        } finally {
            release()
        }
    }

    private fun guarded(target: ModuleState, block: () -> Unit) {
        try {
            block()
            state = target
        } catch (failure: Throwable) {
            Failures.rethrowIfFatal(failure)
            error(failure)
        }
    }

    private fun error(failure: Throwable) {
        lastError = failure
        state = ModuleState.FAILED
        runCatching { context.logger.error("Module $key failed: ${failure.message}", failure) }
    }
}
