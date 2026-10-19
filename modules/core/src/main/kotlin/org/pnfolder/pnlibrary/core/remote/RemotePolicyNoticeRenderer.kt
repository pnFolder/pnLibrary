package org.pnfolder.pnlibrary.core.remote

import org.pnfolder.pnlibrary.api.plugin.DenyAction
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.plugin.RemotePolicy
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation
import org.pnfolder.pnlibrary.console.ConsoleCard
import org.pnfolder.pnlibrary.console.ConsoleTree
import org.pnfolder.pnlibrary.core.ui.ChatTone
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging

/** Prints the console card with the result of a remote compatibility check. */
internal class RemotePolicyNoticeRenderer(private val logging: PlatformLogging) {
    fun render(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicy,
        allowed: Boolean,
        explanation: RemotePolicyExplanation,
    ) {
        val action = when {
            allowed -> "Плагин продолжает работу"
            policy.onDeny == DenyAction.DISABLE_PLUGIN -> "Плагин безопасно отключён"
            else -> "Модуль безопасно остановлен"
        }
        val theme = ConsoleStyle.theme(if (allowed) ChatTone.SUCCESS else ChatTone.FAILURE)

        ConsoleCard.builder(theme, "ПРОВЕРКА СОВМЕСТИМОСТИ")
            .mascot(
                if (allowed) "^.^" else "x.x",
                metadata.name,
                if (allowed) "версия поддерживается" else "для запуска требуется обновление",
            )
            .blank()
            .detail("Установлена", metadata.version)
            .lastDetail("Состояние", if (allowed) "совместима" else "не поддерживается")
            .blank()
            .section(if (allowed) "Результат" else "Почему запуск остановлен")
            .tree(explanation.toConsoleTree())
            .blank()
            .status(action)
            .build()
            .send { line -> logging.console(owner, line) }
    }

    private fun RemotePolicyExplanation.toConsoleTree(): ConsoleTree {
        val tree = ConsoleTree.builder(text)
        children.forEach { child -> tree.child(child.toConsoleTree()) }
        return tree.build()
    }
}
