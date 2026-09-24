package ru.privatenull.pnlibrary.compatibility.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.console.ConsoleCard;
import ru.privatenull.pnlibrary.console.ConsoleTheme;

/** Standalone startup guard. Shade this module into the consuming plugin. */
public final class PnLibraryCompatibility {
    private PnLibraryCompatibility() {}

    public static boolean require(JavaPlugin plugin, String minimumVersion) {
        return require(plugin, CompatibilityOptions.builder(minimumVersion).build());
    }

    public static boolean require(JavaPlugin plugin, CompatibilityOptions options) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(options.pluginName());
        if (dependency == null || !dependency.isEnabled()) {
            reject(plugin, options, null, "библиотека не установлена или отключена");
            disable(plugin);
            return false;
        }
        String installed = dependency.getDescription().getVersion();
        if (!CompatibilityVersion.accepts(installed, options.minimumVersion())) {
            reject(plugin, options, installed, "установлена несовместимая версия");
            disable(plugin);
            return false;
        }
        return true;
    }

    private static void disable(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }

    private static void reject(JavaPlugin plugin, CompatibilityOptions options, String installed, String reason) {
        ConsoleCard.Builder card = ConsoleCard.builder(theme(), "ТРЕБУЕТСЯ ОБНОВЛЕНИЕ")
                .mascot("( x.x )", plugin.getName() + " › " + options.pluginName() + " несовместима", "запуск плагина остановлен")
                .blank()
                .section("Причина")
                .lastItem(reason)
                .detail("Установлена", installed == null ? "не найдена" : installed)
                .detail("Требуется", options.minimumVersion())
                .lastDetail("Состояние", "обновление обязательно")
                .blank()
                .section("Как исправить")
                .item("Скачайте совместимую версию библиотеки")
                .lastItem(options.releasesUrl())
                .blank()
                .status(plugin.getName() + " был отключён");
        card.build().send(plugin.getServer().getConsoleSender()::sendMessage);
    }

    private static ConsoleTheme theme() {
        return new ConsoleTheme(ChatColor.DARK_RED.toString(), ChatColor.RED.toString(),
                ChatColor.WHITE.toString(), ChatColor.GRAY.toString(), ChatColor.RESET.toString());
    }
}
