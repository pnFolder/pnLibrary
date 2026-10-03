package ru.privatenull.pnlibrary.core.downloads

internal data class DownloadFailureDetails(
    val headline: String,
    val reason: String,
    val userAction: String,
    val developerAction: String,
)

internal object DownloadFailureInterpreter {
    private val pluginMismatch = Regex("скачанный JAR объявляет (.+), ожидался (.+)")
    private val versionMismatch = Regex("версия скачанного (.+) ([^ ]+) не соответствует требованию (.+)")

    fun explain(error: Throwable): DownloadFailureDetails {
        val raw = error.message?.trim().orEmpty()
        pluginMismatch.matchEntire(raw)?.let { match ->
            val actual = match.groupValues[1]
            val expected = match.groupValues[2]
            return DownloadFailureDetails(
                "плагин не прошёл проверку",
                "В скачанном файле указано имя $actual, а ожидалось $expected",
                "Скачай плагин $expected самостоятельно и перезапусти сервер",
                "Проверь идентификатор зависимости $expected и ссылку на JAR. Сообщи разработчику, что ссылка скачивает $actual",
            )
        }
        versionMismatch.matchEntire(raw)?.let { match ->
            val plugin = match.groupValues[1]
            val actual = match.groupValues[2]
            val required = match.groupValues[3]
            return DownloadFailureDetails(
                "версия плагина не подходит",
                "Установлена версия $actual, а требуется $required или выше",
                "Скачай $plugin версии $required или выше и перезапусти сервер",
                "Проверь минимальную версию $required и ссылку на релиз. Сообщи разработчику, что ссылка отдаёт версию $actual",
            )
        }
        val reason = raw.ifBlank { "Не удалось подготовить файл зависимости" }
        return DownloadFailureDetails(
            "не удалось подготовить файл",
            reason,
            "Скачай зависимость вручную и перезапусти сервер",
            "Передай разработчику полный текст причины: $reason",
        )
    }
}
