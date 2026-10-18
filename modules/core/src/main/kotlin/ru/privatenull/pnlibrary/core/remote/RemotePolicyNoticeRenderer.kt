package ru.privatenull.pnlibrary.core.remote

import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTree
import ru.privatenull.pnlibrary.core.ui.ChatTone
import ru.privatenull.pnlibrary.core.ui.ConsoleStyle
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging

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
