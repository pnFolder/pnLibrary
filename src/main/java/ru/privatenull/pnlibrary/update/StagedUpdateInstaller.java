package ru.privatenull.pnlibrary.update;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.plugin.PluginDescriptionFile;

import ru.privatenull.pnlibrary.banner.PluginBanner;

/** Скачивает, проверяет и помещает JAR в серверную папку обновлений. */
final class StagedUpdateInstaller {

    private final PluginBanner.Identity identity;
    private final GitHubReleaseClient github;

    StagedUpdateInstaller(PluginBanner.Identity identity, GitHubReleaseClient github) {
        this.identity = identity;
        this.github = github;
    }

    DownloadResult prepare(LatestRelease release) {
        if (release.downloadUrl() == null) {
            return DownloadResult.failure("В GitHub Release не найден подходящий JAR-файл");
        }

        Path temporaryFile = null;
        try {
            Path currentJar = currentPluginJar();
            Path updateDirectory = identity.plugin().getServer().getUpdateFolderFile()
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(updateDirectory);

            Path target = updateDirectory.resolve(currentJar.getFileName()).normalize();
            if (!target.getParent().equals(updateDirectory)) {
                throw new IllegalStateException("Некорректный путь файла обновления");
            }

            temporaryFile = Files.createTempFile(updateDirectory, "pnlibrary-update-", ".tmp");
            download(release.downloadUrl(), temporaryFile);
            validatePluginJar(temporaryFile);
            replaceStagedFile(temporaryFile, target);
            temporaryFile = null;
            return DownloadResult.success(target.getFileName().toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Скачивание обновления было прервано");
        } catch (Exception exception) {
            return DownloadResult.failure(message(exception));
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private void download(String url, Path destination) throws Exception {
        HttpResponse<InputStream> response = github.download(url);
        GitHubReleaseClient.requireSuccess(response.statusCode(), "Сервер загрузки");

        long declaredSize = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredSize > identity.maxUpdateSizeBytes()) {
            response.body().close();
            throw new IllegalStateException("Файл обновления превышает допустимый размер");
        }

        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > identity.maxUpdateSizeBytes()) {
                    throw new IllegalStateException("Файл обновления превышает допустимый размер");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private Path currentPluginJar() throws Exception {
        Path location = Path.of(identity.plugin().getClass().getProtectionDomain()
                        .getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(location)
                || !location.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalStateException("Не удалось определить текущий JAR плагина");
        }
        return location;
    }

    private void validatePluginJar(Path file) throws Exception {
        try (JarFile jar = new JarFile(file.toFile())) {
            JarEntry descriptor = jar.getJarEntry("plugin.yml");
            if (descriptor == null) descriptor = jar.getJarEntry("paper-plugin.yml");
            if (descriptor == null) {
                throw new IllegalStateException("Скачанный JAR не содержит описания плагина");
            }

            PluginDescriptionFile metadata;
            try (InputStream descriptorStream = jar.getInputStream(descriptor)) {
                metadata = new PluginDescriptionFile(descriptorStream);
            }
            String expectedName = identity.plugin().getPluginMeta().getName();
            if (!metadata.getName().equalsIgnoreCase(expectedName)) {
                throw new IllegalStateException("Скачанный JAR предназначен для другого плагина: "
                        + metadata.getName());
            }
        }
    }

    private static void replaceStagedFile(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporaryFile(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // Остаток безопасен: это только незавершённый временный файл.
        }
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
