package ru.privatenull.pnlibrary.metrics;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Единая управляемая сессия bStats для Bukkit, BungeeCord и Velocity.
 *
 * <p>В API намеренно нет флага {@code enabled}: если владелец плагина вызвал
 * один из фабричных методов, метрики запускаются. При этом штатный глобальный
 * opt-out bStats сохраняется, как того требуют правила bStats.</p>
 *
 * <p>Владелец плагина обязан вручную вызвать {@link #close()} в обработчике
 * выключения. Метод идемпотентен: повторное закрытие ничего не делает.</p>
 */
public final class PluginMetrics implements AutoCloseable {

    private final MetricsPlatform platform;
    private final Consumer<Chart> chartRegistrar;
    private final Runnable shutdown;
    private boolean closed;

    private PluginMetrics(
            MetricsPlatform platform,
            Consumer<Chart> chartRegistrar,
            Runnable shutdown
    ) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.chartRegistrar = Objects.requireNonNull(chartRegistrar, "chartRegistrar");
        this.shutdown = Objects.requireNonNull(shutdown, "shutdown");
    }

    /**
     * Запускает bStats для Bukkit/Paper.
     *
     * @param plugin экземпляр плагина
     * @param serviceId ID проекта на bStats
     * @return запущенная сессия метрик
     */
    public static PluginMetrics bukkit(org.bukkit.plugin.Plugin plugin, int serviceId) {
        Objects.requireNonNull(plugin, "plugin");
        requireServiceId(serviceId);

        ru.privatenull.pnlibrary.metrics.vendor.bukkit.Metrics metrics =
                new ru.privatenull.pnlibrary.metrics.vendor.bukkit.Metrics(plugin, serviceId);
        return new PluginMetrics(
                MetricsPlatform.BUKKIT,
                chart -> metrics.addCustomChart(
                        new ru.privatenull.pnlibrary.metrics.vendor.bukkit.Metrics.SimplePie(
                                chart.id(), chart.value()::get)),
                metrics::shutdown);
    }

    /**
     * Запускает bStats для BungeeCord.
     *
     * @param plugin экземпляр плагина
     * @param serviceId ID проекта на bStats
     * @return запущенная сессия метрик
     */
    public static PluginMetrics bungeeCord(
            net.md_5.bungee.api.plugin.Plugin plugin,
            int serviceId
    ) {
        Objects.requireNonNull(plugin, "plugin");
        requireServiceId(serviceId);

        ru.privatenull.pnlibrary.metrics.vendor.bungeecord.Metrics metrics =
                new ru.privatenull.pnlibrary.metrics.vendor.bungeecord.Metrics(plugin, serviceId);
        return new PluginMetrics(
                MetricsPlatform.BUNGEECORD,
                chart -> metrics.addCustomChart(
                        new ru.privatenull.pnlibrary.metrics.vendor.bungeecord.Metrics.SimplePie(
                                chart.id(), chart.value()::get)),
                metrics::shutdown);
    }

    /**
     * Запускает bStats для Velocity.
     *
     * <p>{@code factory} должен быть получен через dependency injection
     * Velocity. Переданный {@code plugin} — тот же экземпляр, который
     * зарегистрирован менеджером плагинов Velocity.</p>
     *
     * @param plugin экземпляр Velocity-плагина
     * @param factory внедрённая фабрика bStats
     * @param serviceId ID проекта на bStats
     * @return запущенная сессия метрик
     */
    public static PluginMetrics velocity(
            Object plugin,
            ru.privatenull.pnlibrary.metrics.vendor.velocity.Metrics.Factory factory,
            int serviceId
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(factory, "factory");
        requireServiceId(serviceId);

        ru.privatenull.pnlibrary.metrics.vendor.velocity.Metrics metrics =
                factory.make(plugin, serviceId);
        return new PluginMetrics(
                MetricsPlatform.VELOCITY,
                chart -> metrics.addCustomChart(
                        new ru.privatenull.pnlibrary.metrics.vendor.velocity.Metrics.SimplePie(
                                chart.id(), chart.value()::get)),
                metrics::shutdown);
    }

    /** @return платформа этой сессии */
    public MetricsPlatform platform() {
        return platform;
    }

    /**
     * Добавляет строковую диаграмму {@code SimplePie}.
     *
     * @param chartId уникальное название диаграммы
     * @param value поставщик текущего значения
     * @return эта сессия для цепочки вызовов
     */
    public synchronized PluginMetrics simplePie(String chartId, Supplier<String> value) {
        ensureOpen();
        String id = Objects.requireNonNull(chartId, "chartId").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("chartId must not be blank");
        }
        chartRegistrar.accept(new Chart(id, Objects.requireNonNull(value, "value")));
        return this;
    }

    /**
     * Останавливает фоновые задачи bStats. Повторный вызов безопасен.
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        shutdown.run();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Metrics session is closed");
        }
    }

    private static void requireServiceId(int serviceId) {
        if (serviceId <= 0) {
            throw new IllegalArgumentException("bStats serviceId must be positive");
        }
    }

    private record Chart(String id, Supplier<String> value) {
    }
}
