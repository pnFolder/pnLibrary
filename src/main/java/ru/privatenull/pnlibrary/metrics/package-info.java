/**
 * Единая интеграция bStats для Bukkit/Paper, BungeeCord и Velocity.
 *
 * <h2>Bukkit/Paper</h2>
 * <pre>{@code
 * private PluginMetrics metrics;
 *
 * public void onEnable() {
 *     metrics = PluginMetrics.bukkit(this, 12345)
 *             .simplePie("mode", () -> "production");
 * }
 *
 * public void onDisable() {
 *     if (metrics != null) metrics.close();
 * }
 * }</pre>
 *
 * <h2>BungeeCord</h2>
 * <pre>{@code
 * metrics = PluginMetrics.bungeeCord(this, 12345);
 * // В onDisable():
 * if (metrics != null) metrics.close();
 * }</pre>
 *
 * <h2>Velocity</h2>
 * <pre>{@code
 * @Inject
 * public MyPlugin(Metrics.Factory factory) {
 *     this.metricsFactory = factory;
 * }
 *
 * // При инициализации плагина:
 * metrics = PluginMetrics.velocity(this, metricsFactory, 12345);
 * // При ProxyShutdownEvent:
 * if (metrics != null) metrics.close();
 * }</pre>
 *
 * <p>В pnLibrary нет собственной настройки, способной случайно отключить
 * метрики. Глобальная настройка {@code plugins/bStats/config.txt} остаётся
 * штатным выбором владельца сервера и не должна обходиться.</p>
 */
package ru.privatenull.pnlibrary.metrics;

import ru.privatenull.pnlibrary.metrics.vendor.velocity.Metrics;
