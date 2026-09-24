package ru.privatenull.pnlibrary.remote.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.console.ConsoleCard;
import ru.privatenull.pnlibrary.console.ConsoleTheme;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;

/** Downloads, verifies, loads, and executes one replaceable remote policy class. */
public final class RemoteCheckRunner {
    private static final long SIX_HOURS_TICKS = 6L * 60L * 60L * 20L;
    private RemoteCheckRunner() {}

    public static boolean run(JavaPlugin plugin, RemoteCheckOptions options) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("pnlibrary-remote-check-", ".remote");
            download(options, temporary);
            try (RemoteClassLoader loader = RemoteClassLoader.forBytes(Files.readAllBytes(temporary), options.className, RemoteCheck.class.getClassLoader())) {
                Class<?> type = loader.load(options.className);
                if (!RemoteCheck.class.isAssignableFrom(type)) throw new IllegalStateException("remote class must implement RemoteCheck");
                RemoteCheck check = (RemoteCheck) type.newInstance();
                RemoteCheckResult result = check.check(new RemoteCheckContext(plugin, options.values));
                if (result == null || !result.allowed()) {
                    deny(plugin, result == null ? "удалённая проверка не вернула разрешение" : result.message());
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                    return false;
                }
                return true;
            }
        } catch (Throwable error) {
            deny(plugin, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
        }
    }

    /** Runs immediately and refreshes the remote policy every six hours. */
    public static void schedule(JavaPlugin plugin, RemoteCheckOptions options) {
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> run(plugin, options), 0L, SIX_HOURS_TICKS);
    }

    private static void download(RemoteCheckOptions options, Path target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(options.url).openConnection();
        connection.setConnectTimeout(8_000); connection.setReadTimeout(30_000); connection.setInstanceFollowRedirects(true);
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalStateException("remote policy HTTP " + connection.getResponseCode());
        try (InputStream input = connection.getInputStream(); java.io.OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192]; long total = 0; int count;
            while ((count = input.read(buffer)) != -1) { total += count; if (total > options.maxBytes) throw new IllegalStateException("remote policy is too large"); output.write(buffer, 0, count); }
        } finally { connection.disconnect(); }
    }


    private static void deny(JavaPlugin plugin, String reason) {
        ConsoleCard.builder(new ConsoleTheme(ChatColor.DARK_RED.toString(), ChatColor.RED.toString(), ChatColor.WHITE.toString(), ChatColor.GRAY.toString(), ChatColor.RESET.toString()), "УДАЛЁННАЯ ПРОВЕРКА")
                .mascot("( x.x )", plugin.getName() + " › проверка отклонена", "логика получена с удалённого источника")
                .blank().section("Причина").lastItem(reason).blank().status(plugin.getName() + " был отключён")
                .build().send(plugin.getServer().getConsoleSender()::sendMessage);
    }
}
