package acceptance.policy

import ru.privatenull.pnlibrary.api.remote.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult

/** Kotlin equivalent of AcceptancePolicy.java, compiled from source by pnLibrary at runtime. */
class AcceptancePolicy : RemotePolicy {
    override fun check(context: RemotePolicyContext): RemotePolicyResult {
        val installed = context.product.version
        return if (installed.startsWith("2.2.")) {
            RemotePolicyResult.deny(RemotePolicyExplanation.builder("Версия больше не поддерживается")
                .branch("Сравнение версий") { versions ->
                    versions.child("Установлена: $installed")
                    versions.child("Минимальная: 2.3.0")
                }
                .branch("Что делать") { action ->
                    action.child("Скачайте актуальную версию плагина")
                    action.branch("После замены файла") { restart ->
                        restart.child("Полностью остановите сервер")
                        restart.child("Запустите сервер заново")
                    }
                }
                .build())
        } else {
            RemotePolicyResult.allow()
        }
    }
}
