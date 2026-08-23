package ru.privatenull.pnlibrary.update;

record LatestRelease(String version, String pageUrl, String downloadUrl) {
}

record DownloadResult(String stagedFile, String error) {

    static DownloadResult disabled() {
        return new DownloadResult(null, null);
    }

    static DownloadResult success(String stagedFile) {
        return new DownloadResult(stagedFile, null);
    }

    static DownloadResult failure(String error) {
        return new DownloadResult(null, error);
    }
}

record UpdateSnapshot(
        boolean checkCompleted,
        boolean updateAvailable,
        boolean updateDownloaded,
        String latestVersion,
        String pageUrl,
        String downloadUrl,
        String stagedFile,
        String error
    ) {

    static UpdateSnapshot initial() {
        return new UpdateSnapshot(false, false, false, null, null, null, null, null);
    }

    static UpdateSnapshot completedWithoutRelease() {
        return new UpdateSnapshot(true, false, false, null, null, null, null, null);
    }

    static UpdateSnapshot upToDate(LatestRelease release) {
        return new UpdateSnapshot(true, false, false, release.version(), release.pageUrl(),
                release.downloadUrl(), null, null);
    }

    static UpdateSnapshot updateAvailable(LatestRelease release, DownloadResult download) {
        return new UpdateSnapshot(true, true, download.stagedFile() != null, release.version(),
                release.pageUrl(), release.downloadUrl(), download.stagedFile(), download.error());
    }

    UpdateSnapshot withError(String reason) {
        return new UpdateSnapshot(true, updateAvailable, updateDownloaded, latestVersion,
                pageUrl, downloadUrl, stagedFile, reason);
    }
}
