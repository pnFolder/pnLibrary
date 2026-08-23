package ru.privatenull.pnlibrary.update;

/**
 * Получатель событий системы обновлений.
 * Обычно создаётся автоматически {@code PluginBanner} и не требует ручной реализации.
 */
public interface UpdateReporter {

    void updateAvailable(String latestVersion, String releaseUrl);

    void upToDate();

    void updateDownloaded(String latestVersion, String stagedFile);

    void downloadFailed(String reason);

    void checkFailed(String reason);
}
