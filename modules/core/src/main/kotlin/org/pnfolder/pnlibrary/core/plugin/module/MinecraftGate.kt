package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.compat.MinecraftCompatibility
import org.pnfolder.pnlibrary.api.compat.MinecraftSupport
import org.pnfolder.pnlibrary.api.compat.MinecraftSupportRule
import org.pnfolder.pnlibrary.api.compat.NewerAction
import org.pnfolder.pnlibrary.api.compat.UnsupportedAction
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

/**
 * The Minecraft version check of one module. The check itself never changes; only the administrator's "start anyway"
 * does, so it is read from [preferences] each time ([scope] is `plugin:module`).
 *
 * @property warning a sentence for the log when the module starts but the version is doubtful, or `null`
 * @property blockReason why the module may not start, or `null`; ignored while the administrator forces it
 */
internal class MinecraftGate(
    private val scope: String,
    private val preferences: ModulePreferences,
    private val support: MinecraftSupport,
    private val declared: String?,
    private val server: String?,
    val warning: String?,
    private val blockReason: String?,
    val unsupportedAction: UnsupportedAction,
) {
    val forced: Boolean get() = !preferences.autoStart(KEY + scope)

    /** Why the module may not start now, or `null`. */
    fun reason(): String? = if (forced) null else blockReason

    /** Whether registering must fail instead of switching the module off. */
    fun mustFail(controllable: Boolean): Boolean =
        reason() != null && (unsupportedAction == UnsupportedAction.STOP_PLUGIN || !controllable)

    fun setForced(on: Boolean) = preferences.setAutoStart(KEY + scope, !on)

    fun compatibility(): MinecraftCompatibility =
        MinecraftCompatibility(support, declared, server, forced, reason() != null, blockReason ?: warning)

    companion object {
        const val KEY = "mcforce|"

        /** Checks [server] against the module's own rule, or the plugin's when the module declared none. */
        fun open(
            scope: String,
            preferences: ModulePreferences,
            moduleRule: MinecraftSupportRule,
            pluginRule: MinecraftSupportRule,
            server: String?,
        ): MinecraftGate {
            val rule = if (moduleRule.isDeclared) moduleRule else pluginRule
            val unsupported = moduleRule.unsupportedAction ?: pluginRule.unsupportedAction ?: UnsupportedAction.DISABLE_MODULE
            val newer = moduleRule.newerAction ?: pluginRule.newerAction ?: NewerAction.ALLOW_WITH_WARNING
            val info = server?.let(MinecraftVersion::parseInfo)?.takeIf { it.numeric }
            val serverText = info?.let { "${it.major}.${it.minor}" + (if (it.patch != null && it.patch != 0) ".${it.patch}" else "") }
            val support = rule.evaluate(info)
            val declared = rule.description
            var warning: String? = null
            var block: String? = null
            when (support) {
                MinecraftSupport.NEWER_THAN_DECLARED -> when (newer) {
                    NewerAction.ALLOW -> Unit
                    NewerAction.ALLOW_WITH_WARNING -> warning = "Сервер на Minecraft $serverText, это новее проверенных версий ($declared): возможны проблемы."
                    NewerAction.BLOCK -> block = "Нужен Minecraft $declared, на сервере $serverText: он новее."
                }
                MinecraftSupport.OLDER_THAN_DECLARED, MinecraftSupport.EXCLUDED -> block = "Нужен Minecraft $declared, на сервере $serverText."
                else -> Unit
            }
            if (block != null && unsupported == UnsupportedAction.WARN_ONLY) {
                warning = block
                block = null
            }
            return MinecraftGate(scope, preferences, support, declared, serverText ?: server, warning, block, unsupported)
        }
    }
}
