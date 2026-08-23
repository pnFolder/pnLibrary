package ru.privatenull.pnlibrary.banner;

import ru.privatenull.pnlibrary.update.UpdateReporter;

/** Передаёт события updater в консольный renderer баннера. */
final class BannerUpdateReporter implements UpdateReporter {

    private final BannerRenderer renderer;

    BannerUpdateReporter(BannerRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void updateAvailable(String latestVersion, String releaseUrl) {
        renderer.update(latestVersion, releaseUrl);
    }

    @Override
    public void upToDate() {
        renderer.upToDate();
    }

    @Override
    public void updateDownloaded(String latestVersion, String stagedFile) {
        renderer.updateDownloaded(latestVersion, stagedFile);
    }

    @Override
    public void downloadFailed(String reason) {
        renderer.updateDownloadFailure(reason);
    }

    @Override
    public void checkFailed(String reason) {
        renderer.updateCheckFailure(reason);
    }
}
