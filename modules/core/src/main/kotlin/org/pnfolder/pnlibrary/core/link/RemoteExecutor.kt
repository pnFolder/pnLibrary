package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateState
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Carries out the update controls another server asked for, on this server.
 *
 * It does only what [RemoteAction] lists, through the same update service the commands use, and it records who asked
 * in the update history. A request id is accepted once, so a recorded request cannot be played again.
 */
class RemoteExecutor(private val library: PnLibrary, private val clock: () -> Instant = Instant::now) {
    private val seen = ConcurrentHashMap<UUID, Instant>()

    /** Runs [request] and passes the answer to [reply], possibly from another thread. */
    fun run(request: RemoteRequest, reply: (RemoteResult) -> Unit) {
        val now = clock()
        seen.values.removeIf { Duration.between(it, now) > REMEMBER }
        if (seen.putIfAbsent(request.id, now) != null) return reply(result(request, false, "Этот запрос уже выполнялся."))
        val actor = UpdateActor(UpdateActor.Kind.INTEGRATION, "remote:${request.origin}", "${request.actor} (${request.origin.ifEmpty { "сервер" }})")
        val product = request.product.orEmpty()
        try {
            when (request.action) {
                RemoteAction.CHECK -> library.updates.checkNow(actor).whenComplete { plan, error ->
                    reply(
                        if (error != null) {
                            result(request, false, "Проверка не удалась: ${message(error)}")
                        } else {
                            val count = plan.plan?.changes?.size ?: 0
                            result(request, true, if (count == 0) "Всё актуально." else "Найдено обновлений: $count.")
                        },
                    )
                }
                RemoteAction.UPDATE -> update(request, product, actor, reply)
                RemoteAction.AUTO_ON, RemoteAction.AUTO_OFF -> {
                    val enabled = request.action == RemoteAction.AUTO_ON
                    library.updates.setAutomaticDownload(product, enabled, actor).whenComplete { _, error ->
                        reply(
                            if (error != null) {
                                result(request, false, "Не удалось изменить: ${message(error)}")
                            } else {
                                result(request, true, "Автозагрузка $product: ${if (enabled) "включена" else "выключена"}.")
                            },
                        )
                    }
                }
            }
        } catch (error: Exception) {
            reply(result(request, false, message(error)))
        }
    }

    private fun update(request: RemoteRequest, product: String, actor: UpdateActor, reply: (RemoteResult) -> Unit) {
        if (library.updates.get(product) == null) return reply(result(request, false, "Плагин $product не подключён к обновлениям pnLibrary."))
        library.updates.checkNow(actor).whenComplete { plan, error ->
            val change = plan?.plan?.changes?.firstOrNull { it.product.value.equals(product, ignoreCase = true) }
            when {
                error != null -> reply(result(request, false, "Проверка не удалась: ${message(error)}"))
                plan.state != UpdateState.UPDATE_AVAILABLE || change == null -> reply(result(request, true, "Для $product нет обновления."))
                else -> library.updates.stage(plan.id, actor).whenComplete { _, stageError ->
                    reply(
                        if (stageError != null) {
                            result(request, false, "Не удалось подготовить: ${message(stageError)}")
                        } else {
                            result(request, true, "Подготовлено: $product v${change.from} » v${change.to}. Нужен перезапуск.")
                        },
                    )
                }
            }
        }
    }

    private fun result(request: RemoteRequest, ok: Boolean, text: String) = RemoteResult(request.id, request.origin, ok, text, clock())

    private fun message(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.last()
        return cause.message ?: cause.javaClass.simpleName
    }

    private companion object {
        val REMEMBER: Duration = Duration.ofMinutes(10)
    }
}
