package org.pnfolder.pnlibrary.bootstrap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.pnfolder.pnlibrary.console.ConsoleCard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform-neutral part of the embedded installer: checks the installed version, downloads and verifies the release
 * universal jar, checks it carries the descriptor of one {@link BootstrapPlatform}, puts it into {@code plugins} and reports every step as a console card.
 *
 * The platform entry points ({@code PnLibraryBootstrap} of Bukkit, BungeeCord and Velocity) only look pnLibrary up
 * in their plugin manager and, where the platform allows it, load the downloaded jar.
 */
public final class LibraryInstaller {
    private static final Pattern YAML_NAME = Pattern.compile("(?m)^name:\\s*['\"]?([^'\"\\r\\n]+?)['\"]?\\s*$");

    private final BootstrapPlatform platform;
    private final BootstrapOptions options;
    private final String pluginName;
    private final BootstrapConsole console;

    /**
     * Creates an installer for one plugin.
     *
     * @param platform platform whose release jar is installed
     * @param options minimum version, repository and size limit
     * @param pluginName name of the plugin that needs pnLibrary, for messages
     * @param console where the cards are printed
     */
    public LibraryInstaller(BootstrapPlatform platform, BootstrapOptions options, String pluginName, BootstrapConsole console) {
        this.platform = platform;
        this.options = options;
        this.pluginName = pluginName;
        this.console = console;
    }

    /**
     * Checks the pnLibrary the platform has loaded and explains in the console what is wrong, if anything.
     *
     * @param installedVersion version of the loaded pnLibrary, or {@code null} when it is present but not running
     * @return {@code true} when it runs and is at least the minimum version
     */
    public boolean checkInstalled(String installedVersion) {
        if (installedVersion == null) {
            failed(new IllegalStateException(BootstrapDefaults.PRODUCT_NAME + " установлена, но не запустилась: смотрите её ошибки выше в логе"));
            return false;
        }
        boolean compatible;
        try {
            compatible = BootstrapVersion.isAtLeast(installedVersion, options.minimumVersion());
        } catch (IllegalArgumentException invalid) {
            compatible = false;
        }
        if (compatible) return true;
        ConsoleCard.builder(console.theme(BootstrapConsole.Tone.FAILURE), "PNLIBRARY УСТАРЕЛА")
                .mascot("( -.- )", pluginName + " › нужна более новая " + BootstrapDefaults.PRODUCT_NAME, "запуск плагина остановлен")
                .blank()
                .detail("Установлена", installedVersion)
                .lastDetail("Нужна", "от " + options.minimumVersion())
                .section("Что делать")
                .item("Обновите " + BootstrapDefaults.PRODUCT_NAME + " командой " + platform.updateHint())
                .lastItem("или скачайте вручную: " + releasesPage())
                .section("Не получается?")
                .lastItem("Напишите в поддержку: " + options.supportUrl())
                .blank()
                .status(pluginName + " не был запущен")
                .build().send(console::send);
        return false;
    }

    /**
     * Downloads the release jar for this platform, verifies its size, SHA-256 and descriptor, and moves it into
     * {@code pluginsDirectory}. A jar already there under the same name is kept until {@link Placement#commit()}.
     *
     * @param pluginsDirectory the server's {@code plugins} directory
     * @return the placed jar, which can be rolled back
     * @throws Exception when GitHub is unreachable or the file does not verify; nothing is left behind
     */
    public Placement download(Path pluginsDirectory) throws Exception {
        GitHubReleaseClient client = new GitHubReleaseClient(options, platform);
        LibraryRelease release = client.release();
        Path directory = pluginsDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(release.fileName()).normalize();
        if (!directory.equals(target.getParent()) || !release.fileName().matches("[A-Za-z0-9._-]+\\.jar")) {
            throw new IllegalStateException("unsafe release filename: " + release.fileName());
        }

        ConsoleCard.builder(console.theme(BootstrapConsole.Tone.PROGRESS), "УСТАНОВКА PNLIBRARY")
                .mascot("( o.o )", pluginName + " › подготовка зависимости", BootstrapDefaults.PRODUCT_NAME + " не найдена на сервере")
                .blank()
                .detail("Источник", release.uri().getHost())
                .detail("Платформа", platform.displayName())
                .detail("Выпуск", release.stable() ? "стабильный" : "предварительный (нестабильный)")
                .detail("Версия", release.version())
                .lastDetail("Файл", release.fileName())
                .blank()
                .status("Скачиваю и проверяю библиотеку…")
                .build().send(console::send);

        Path temporary = Files.createTempFile(directory, ".pnlibrary-bootstrap-", ".tmp");
        try {
            client.download(release.uri(), temporary);
            long downloaded = Files.size(temporary);
            if (release.size() >= 0 && downloaded != release.size()) throw new IllegalStateException("downloaded size does not match the release metadata");
            if (!sha256(temporary).equals(release.sha256())) throw new IllegalStateException("downloaded SHA-256 does not match the release checksum");
            release = release.withSize(downloaded);
            validateJar(temporary);
            Path previous = null;
            if (Files.exists(target)) {
                previous = target.resolveSibling(target.getFileName() + ".previous");
                Files.copy(target, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            moveAtomically(temporary, target);
            return new Placement(release, target, previous);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reports a finished installation.
     *
     * @param placement the installed jar
     * @param restartRequired {@code true} on platforms that cannot load a plugin while running
     */
    public void installed(Placement placement, boolean restartRequired) {
        long bytes = placement.release().size();
        int countdown = restartRequired ? options.restartCountdown() : 0;
        ConsoleCard.Builder card = ConsoleCard.builder(
                        console.theme(restartRequired ? BootstrapConsole.Tone.PROGRESS : BootstrapConsole.Tone.SUCCESS),
                        restartRequired ? "НУЖЕН ПЕРЕЗАПУСК" : "PNLIBRARY ГОТОВА")
                .mascot(restartRequired ? "( o.o )" : "( ^.^ )", BootstrapDefaults.PRODUCT_NAME + " установлена",
                        restartRequired ? platform.displayName() + " подключит её после перезапуска" : "общая система pnFolder подключена")
                .blank()
                .detail("Версия", placement.release().version())
                .detail("Выпуск", placement.release().stable() ? "стабильный" : "предварительный (нестабильный)")
                .detail("Файл", placement.release().fileName())
                .lastDetail("Размер", String.format(Locale.US, "%.2f MiB", bytes / 1048576.0))
                .blank();
        if (!placement.release().stable()) {
            card.section("Это предварительная версия")
                    .item("Стабильного релиза " + BootstrapDefaults.PRODUCT_NAME + " пока нет, поэтому установлена " + placement.release().version())
                    .item("В ней возможны ошибки и изменения, на основном сервере сначала проверьте работу")
                    .lastItem("Когда выйдет стабильная версия, обновите её: " + platform.updateHint())
                    .blank();
        }
        if (restartRequired && countdown > 0) {
            card.section("Перезапуск")
                    .item(platform.displayName() + " остановится через " + countdown + " с")
                    .lastItem("снова запустит его панель хостинга или скрипт запуска");
        } else if (restartRequired) {
            card.section("Что делать")
                    .item("Перезапустите " + platform.displayName() + ", когда будет удобно")
                    .lastItem("команда в консоли: " + platform.stopCommand());
        }
        card.status(restartRequired ? pluginName + " запустится после перезапуска" : pluginName + " продолжает запуск")
                .build().send(console::send);
    }

    /**
     * One line of the restart countdown, for the console and online players.
     *
     * @param secondsLeft seconds until the proxy stops
     * @return for example {@code [pnLibrary] Перезапуск прокси через 3 с: установлена pnLibrary для pnCases}
     */
    public String countdownLine(int secondsLeft) {
        return "[" + BootstrapDefaults.PRODUCT_NAME + "] Перезапуск прокси через " + secondsLeft + " с: установлена " + BootstrapDefaults.PRODUCT_NAME + " для " + pluginName;
    }

    /**
     * Message shown to players when the proxy stops.
     *
     * @return the reason for the stop
     */
    public String stopReason() {
        return "Перезапуск: установлена " + BootstrapDefaults.PRODUCT_NAME + " для " + pluginName + ". Зайдите через минуту.";
    }

    /**
     * Whether the proxy should stop itself after the installation reported by {@link #installed}.
     *
     * @return the countdown in seconds, or {@code 0}
     */
    public int restartCountdown() { return options.restartCountdown(); }

    /**
     * Whether a countdown line is worth printing: every ten seconds, then each of the last five.
     *
     * @param secondsLeft seconds until the proxy stops
     * @return {@code true} when the line should be shown
     */
    public static boolean announces(int secondsLeft) {
        return secondsLeft <= 5 || secondsLeft % 10 == 0;
    }

    /**
     * Reports why pnLibrary is not available.
     *
     * @param error the cause
     */
    public void failed(Throwable error) {
        String reason = error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? error.getClass().getSimpleName() : error.getMessage();
        ConsoleCard.builder(console.theme(BootstrapConsole.Tone.FAILURE), "ОШИБКА ЗАПУСКА")
                .mascot("( x.x )", pluginName + " › " + BootstrapDefaults.PRODUCT_NAME + " недоступна", "запуск плагина остановлен")
                .blank()
                .section("Причина")
                .lastItem(reason)
                .section("Что делать")
                .item("Проверьте доступ сервера к GitHub")
                .item("Скачайте jar для " + platform.displayName() + " вручную")
                .lastItem(releasesPage())
                .section("Не получается?")
                .lastItem("Напишите в поддержку: " + options.supportUrl())
                .blank()
                .status(pluginName + " не был запущен")
                .build().send(console::send);
    }

    private String releasesPage() {
        return "https://github.com/" + options.repositoryOwner() + "/" + options.repositoryName() + "/releases/latest";
    }

    private void validateJar(Path path) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry(platform.descriptor());
            if (entry == null) throw new IllegalStateException("downloaded JAR has no " + platform.descriptor());
            String declared;
            try (InputStream input = jar.getInputStream(entry)) {
                declared = declaredName(platform, new String(readAll(input), StandardCharsets.UTF_8));
            }
            if (!platform.pluginName().equals(declared)) {
                throw new IllegalStateException("downloaded JAR declares plugin " + declared);
            }
        }
    }

    /** The plugin name of a {@code plugin.yml} or {@code bungee.yml}, or the id of a {@code velocity-plugin.json}. */
    static String declaredName(BootstrapPlatform platform, String descriptor) {
        if (platform == BootstrapPlatform.VELOCITY) {
            JsonElement json = JsonParser.parseString(descriptor);
            JsonElement id = json.isJsonObject() ? json.getAsJsonObject().get("id") : null;
            return id == null || id.isJsonNull() ? null : id.getAsString();
        }
        Matcher match = YAML_NAME.matcher(descriptor);
        return match.find() ? match.group(1).trim() : null;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
            if (output.size() > 1024 * 1024) throw new IOException("plugin descriptor is too large");
        }
        return output.toByteArray();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    /** A jar placed into {@code plugins}; keeps the file it replaced until the platform confirms it works. */
    public static final class Placement {
        private final LibraryRelease release;
        private final Path path;
        private final Path previous;

        Placement(LibraryRelease release, Path path, Path previous) {
            this.release = release;
            this.path = path;
            this.previous = previous;
        }

        /** Returns the release that was installed.
         * @return its version, file name and digest */
        public LibraryRelease release() { return release; }

        /** Returns where the jar was placed.
         * @return the path inside {@code plugins} */
        public Path path() { return path; }

        /** Keeps the new jar and deletes the copy of the replaced one.
         * @throws IOException when the copy cannot be deleted */
        public void commit() throws IOException {
            if (previous != null) Files.deleteIfExists(previous);
        }

        /** Puts the replaced jar back, or removes the new one when nothing was replaced.
         * @throws IOException when the file cannot be restored */
        public void rollback() throws IOException {
            if (previous != null) moveAtomically(previous, path);
            else Files.deleteIfExists(path);
        }
    }
}
