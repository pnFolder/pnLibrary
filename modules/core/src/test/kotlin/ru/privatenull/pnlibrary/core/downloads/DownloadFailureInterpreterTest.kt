package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadFailureInterpreterTest {
    @Test
    fun `explains plugin identity mismatch without exposing an opaque exception`() {
        val details = DownloadFailureInterpreter.explain(
            IllegalArgumentException("скачанный JAR объявляет WrongPlugin, ожидался Vault"),
        )

        assertEquals("плагин не прошёл проверку", details.headline)
        assertEquals("В скачанном файле указано имя WrongPlugin, а ожидалось Vault", details.reason)
        assertEquals("Скачай плагин Vault самостоятельно и перезапусти сервер", details.userAction)
    }

    @Test
    fun `preserves an unknown failure as an actionable fallback`() {
        val details = DownloadFailureInterpreter.explain(IllegalStateException("соединение разорвано"))

        assertEquals("не удалось подготовить файл", details.headline)
        assertEquals("соединение разорвано", details.reason)
        assertEquals(
            "Передай разработчику полный текст причины: соединение разорвано",
            details.developerAction,
        )
    }
}
