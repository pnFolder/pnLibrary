package pnfolder.policy

import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.RemotePolicy
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation
import org.pnfolder.pnlibrary.api.remote.RemotePolicyResult
import org.pnfolder.pnlibrary.api.version.SemanticVersion

/**
 * Демонстрационная политика самой pnLibrary, не подключённая автоматически.
 * Версии и ограничения ниже — примеры, а не сведения о настоящих релизах.
 * Перед подключением замени их своими правилами: текущая dev-сборка может быть запрещена.
 * Политика только возвращает решение; остановкой библиотеки занимается общий runtime.
 */
class PnLibraryPolicy : RemotePolicy {
    override fun check(context: RemotePolicyContext): RemotePolicyResult {
        val problems = mutableListOf<RemotePolicyExplanation>()
        val installed = SemanticVersion.tryParse(context.product.version)

        if (!context.product.id.equals("pnlibrary", ignoreCase = true)) {
            problems += reason("Политика подключена не к тому продукту",
                "Ожидается: pnlibrary", "Получен: ${context.product.id}")
        }

        if (installed == null) {
            problems += reason("Не удалось определить версию библиотеки",
                "Получена: ${context.product.version}", "Ожидается SemVer, например 1.2.0")
        } else {
            if (installed < SemanticVersion.parse(MINIMUM_VERSION) || installed >= SemanticVersion.parse(MAXIMUM_EXCLUSIVE)) {
                problems += reason("Версия библиотеки вне поддерживаемого диапазона",
                    "Установлена: $installed", "Разрешены: от $MINIMUM_VERSION включительно до $MAXIMUM_EXCLUSIVE не включительно")
            }
            if (installed.prerelease.isNotEmpty()) {
                problems += reason("Тестовые сборки запрещены этой политикой",
                    "Установлена: $installed", "Установите стабильную версию без dev, alpha, beta или rc")
            }
            if (installed.toString() in WITHDRAWN_VERSIONS) {
                problems += reason("Эта сборка отозвана",
                    "Установлена: $installed", "Тестовый пример: для этой версии обнаружена критическая ошибка")
            }
        }

        val java = System.getProperty("java.specification.version", "unknown")
        val javaFeature = java.removePrefix("1.").substringBefore('.').toIntOrNull()
        if (javaFeature == null || javaFeature < MINIMUM_JAVA) {
            problems += reason("Неподдерживаемая Java",
                "Запущена: $java", "Требуется: Java $MINIMUM_JAVA или новее")
        }

        if (context.platform.type !in ALLOWED_PLATFORMS) {
            problems += reason("Платформа не разрешена",
                "Запущена: ${context.platform.name}", "Разрешены: ${ALLOWED_PLATFORMS.joinToString()}")
        }

        // Необязательный бизнес-флаг из RemotePolicy.builder().value("maintenance", "true").
        if (context.values["maintenance"].equals("true", ignoreCase = true)) {
            problems += reason("Работа библиотеки временно приостановлена",
                "Владелец политики включил режим обслуживания")
        }

        if (problems.isEmpty()) return RemotePolicyResult.allow()

        val explanation = RemotePolicyExplanation.builder("pnLibrary не прошла проверку политики")
        problems.forEach { explanation.child(it) }
        explanation.branch("Что делать") { action ->
            action.child("Изучите причины выше и исправьте версию или окружение")
            action.child("Если меняете JAR, полностью остановите сервер перед заменой")
            action.child("После исправления запустите сервер заново")
        }
        return RemotePolicyResult.deny(explanation.build())
    }

    private fun reason(title: String, vararg details: String): RemotePolicyExplanation {
        val branch = RemotePolicyExplanation.builder(title)
        details.forEach { branch.child(it) }
        return branch.build()
    }

    private companion object {
        const val MINIMUM_VERSION = "1.0.0"
        const val MAXIMUM_EXCLUSIVE = "2.0.0"
        const val MINIMUM_JAVA = 17
        val WITHDRAWN_VERSIONS = setOf("1.1.3")
        val ALLOWED_PLATFORMS = setOf(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)
    }
}
