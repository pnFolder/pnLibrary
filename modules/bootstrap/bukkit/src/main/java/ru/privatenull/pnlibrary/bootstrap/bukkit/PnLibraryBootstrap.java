package ru.privatenull.pnlibrary.bootstrap.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.console.ConsoleCard;
import ru.privatenull.pnlibrary.console.ConsoleTheme;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarFile;

/**
 * Installs and starts a verified Bukkit pnLibrary runtime before the embedding plugin uses its API.
 * This class is designed to be shaded into the consuming plugin; it has no dependency on pnLibrary itself.
 */
public final class PnLibraryBootstrap {
    private static final String PLUGIN_NAME = "pnLibrary";

    private PnLibraryBootstrap() {}

    public static boolean ensureInstalled(JavaPlugin plugin, String minimumVersion) {
        return ensureInstalled(plugin, BootstrapOptions.builder(minimumVersion).build());
    }

    public static boolean ensureInstalled(JavaPlugin plugin, BootstrapOptions options) {
        Plugin installed = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (installed != null) return ensureCompatible(plugin, options.minimumVersion());
        try {
            install(plugin, options);
            return true;
        } catch (Throwable error) {
            failure(plugin, error);
            return false;
        }
    }

    public static boolean ensureCompatible(JavaPlugin plugin, String minimumVersion) {
        Plugin library = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (library == null || !library.isEnabled()) {
            failure(plugin, new IllegalStateException("pnLibrary is not installed or failed to enable"));
            return false;
        }
        if (BootstrapVersion.isAtLeast(library.getDescription().getVersion(), minimumVersion)) return true;
        failure(plugin, new IllegalStateException("installed pnLibrary " + library.getDescription().getVersion()
                + " is older than required " + BootstrapVersion.normalize(minimumVersion)));
        return false;
    }

    private static void install(JavaPlugin plugin, BootstrapOptions options) throws Exception {
        GitHubReleaseClient.Release release = new GitHubReleaseClient(options).release();
        Path pluginsDirectory = plugin.getDataFolder().getParentFile().toPath().toAbsolutePath().normalize();
        Files.createDirectories(pluginsDirectory);
        Path target = pluginsDirectory.resolve(release.fileName).normalize();
        if (!target.getParent().equals(pluginsDirectory) || !release.fileName.matches("[A-Za-z0-9._-]+\\.jar")) {
            throw new IllegalStateException("unsafe release filename: " + release.fileName);
        }

        start(plugin, release);
        Path temporary = Files.createTempFile(pluginsDirectory, ".pnlibrary-bootstrap-", ".tmp");
        try {
            new GitHubReleaseClient(options).download(release.uri, temporary);
            long actualSize = Files.size(temporary);
            if (actualSize != release.size) throw new IllegalStateException("downloaded size does not match GitHub metadata");
            if (!sha256(temporary).equals(release.sha256)) throw new IllegalStateException("downloaded SHA-256 does not match GitHub digest");
            validateJar(temporary);
            moveAtomically(temporary, target);
            Plugin loaded = plugin.getServer().getPluginManager().loadPlugin(target.toFile());
            if (loaded == null || !PLUGIN_NAME.equals(loaded.getName())) {
                throw new IllegalStateException("downloaded JAR is not the pnLibrary Bukkit plugin");
            }
            plugin.getServer().getPluginManager().enablePlugin(loaded);
            if (!loaded.isEnabled()) throw new IllegalStateException("pnLibrary was installed but failed to enable");
            success(plugin, loaded.getDescription().getVersion(), release.fileName, actualSize);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateJar(Path path) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            java.util.jar.JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) throw new IllegalStateException("downloaded JAR has no plugin.yml");
            try (InputStream input = jar.getInputStream(entry)) {
                PluginDescriptionFile description = new PluginDescriptionFile(input);
                if (!PLUGIN_NAME.equals(description.getName())) {
                    throw new IllegalStateException("downloaded JAR declares plugin " + description.getName());
                }
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws Exception {
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

    private static void start(JavaPlugin plugin, GitHubReleaseClient.Release release) {
        ConsoleCard.builder(theme(ChatColor.GOLD, ChatColor.YELLOW), "УСТАНОВКА PNLIBRARY")
                .mascot("( o.o )", plugin.getName() + " › подготовка зависимости", "pnLibrary не найдена на сервере")
                .blank()
                .detail("Источник", "GitHub Releases")
                .detail("Канал", release.stable ? "stable" : "точная версия")
                .detail("Версия", release.version)
                .lastDetail("Файл", release.fileName)
                .blank()
                .status("Скачиваю и проверяю библиотеку…")
                .build().send(plugin.getServer().getConsoleSender()::sendMessage);
    }

    private static void success(JavaPlugin plugin, String version, String file, long bytes) {
        ConsoleCard.builder(theme(ChatColor.DARK_GREEN, ChatColor.GREEN), "PNLIBRARY ГОТОВА")
                .mascot("( ^.^ )", "pnLibrary установлена", "общая система pnFolder подключена")
                .blank()
                .detail("Версия", version)
                .detail("Файл", file)
                .detail("Размер", String.format(Locale.US, "%.2f MiB", bytes / 1048576.0))
                .lastDetail("Состояние", "включена")
                .blank()
                .status(plugin.getName() + " продолжает запуск")
                .build().send(plugin.getServer().getConsoleSender()::sendMessage);
    }

    private static void failure(JavaPlugin plugin, Throwable error) {
        String reason = error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? error.getClass().getSimpleName() : error.getMessage();
        ConsoleCard.builder(theme(ChatColor.DARK_RED, ChatColor.RED), "ОШИБКА ЗАПУСКА")
                .mascot("( x.x )", plugin.getName() + " › pnLibrary недоступна", "запуск плагина остановлен")
                .blank()
                .section("Причина")
                .lastItem(reason)
                .section("Что делать")
                .item("Проверьте доступ сервера к GitHub")
                .item("Скачайте Bukkit JAR вручную")
                .lastItem("https://github.com/pnFolder/pnLibrary/releases/latest")
                .blank()
                .status(plugin.getName() + " не был запущен")
                .build().send(plugin.getServer().getConsoleSender()::sendMessage);
    }

    private static ConsoleTheme theme(ChatColor border, ChatColor accent) {
        return new ConsoleTheme(border.toString(), accent.toString(), ChatColor.WHITE.toString(),
                ChatColor.GRAY.toString(), ChatColor.RESET.toString());
    }
}
