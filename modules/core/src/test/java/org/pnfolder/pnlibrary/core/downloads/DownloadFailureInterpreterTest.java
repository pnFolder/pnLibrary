package org.pnfolder.pnlibrary.core.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DownloadFailureInterpreterTest {
    @Test
    void explainsPluginIdentityMismatchWithoutExposingAnOpaqueException() {
        DownloadFailureDetails details = DownloadFailureInterpreter.explain(
            new IllegalArgumentException("скачанный JAR объявляет WrongPlugin, ожидался Vault"));

        assertEquals("плагин не прошёл проверку", details.getHeadline());
        assertEquals("В скачанном файле указано имя WrongPlugin, а ожидалось Vault", details.getReason());
        assertEquals("Скачай плагин Vault самостоятельно и перезапусти сервер", details.getUserAction());
    }

    @Test
    void preservesAnUnknownFailureAsAnActionableFallback() {
        DownloadFailureDetails details = DownloadFailureInterpreter.explain(new IllegalStateException("соединение разорвано"));

        assertEquals("не удалось подготовить файл", details.getHeadline());
        assertEquals("соединение разорвано", details.getReason());
        assertEquals("Передай разработчику полный текст причины: соединение разорвано", details.getDeveloperAction());
    }
}
