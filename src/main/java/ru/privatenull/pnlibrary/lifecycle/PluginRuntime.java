package ru.privatenull.pnlibrary.lifecycle;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import ru.privatenull.pnlibrary.banner.PluginBanner;
import ru.privatenull.pnlibrary.database.DatabaseExecutor;
import ru.privatenull.pnlibrary.metrics.PluginMetrics;
import ru.privatenull.pnlibrary.logging.MBox;
import ru.privatenull.pnlibrary.logging.PluginLogger;
import ru.privatenull.pnlibrary.update.PluginUpdateService;

/**
 * Подключает доступную инфраструктуру к {@link PluginBanner.Identity}.
 * Ничего не извлекает из {@code plugin.yml} и не придумывает автоматически.
 * Неуказанные GitHub и bStats считаются намеренно отключёнными модулями.
 */
public final class PluginRuntime implements AutoCloseable {

    private final PluginBanner.Identity identity;
    private final JavaPlugin plugin;
    private final PluginLogger logger;
    private final PluginUpdateService updates;
    private final PluginMetrics metrics;
    private DatabaseExecutor databaseExecutor;
    private boolean closed;

    private PluginRuntime(
            PluginBanner.Identity identity,
            Consumer<PluginBanner.Data> startupSetup
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(startupSetup, "startupSetup");
        plugin = identity.plugin();
        logger = identity.log();

        PluginMetrics startedMetrics = null;
        PluginUpdateService startedUpdates = null;
        try {
            PluginBanner.Data startup = new PluginBanner.Data(identity);
            Integer bStatsPluginId = identity.bStatsPluginId();
            if (bStatsPluginId == null) {
                startup.skip("bStats", "ID проекта не указан");
            } else {
                startedMetrics = PluginMetrics.bukkit(plugin, bStatsPluginId);
                startup.ok("bStats", "Метрики запущены");
            }

            if (identity.github() == null) {
                startup.skip("Обновления", "GitHub-репозиторий не указан");
            } else {
                startup.ok("Обновления", "Проверка GitHub запущена");
            }

            startupSetup.accept(startup);
            PluginBanner.broadcastEnable(startup);
            startedUpdates = identity.github() == null ? null : identity.updates();
        } catch (RuntimeException | Error exception) {
            if (startedUpdates != null) startedUpdates.close();
            if (startedMetrics != null) startedMetrics.close();
            throw exception;
        }
        metrics = startedMetrics;
        updates = startedUpdates;
    }

    /**
     * Запускает runtime из готового идентификатора.
     *
     * <pre>{@code
     * PluginBanner.Identity identity = new PluginBanner.Identity(this, "PnFolder");
     * // github(...) и bStats(...) подключаются только при необходимости.
     * runtime = PluginRuntime.start(identity);
     * }</pre>
     *
     * @param identity полностью настроенный идентификатор
     * @return запущенный runtime
     */
    public static PluginRuntime start(PluginBanner.Identity identity) {
        return new PluginRuntime(identity, startup -> { });
    }

    /**
     * Запускает runtime и добавляет пользовательские компоненты прямо в
     * основной баннер включения.
     *
     * <pre>{@code
     * runtime = PluginRuntime.start(identity, startup -> startup
     *         .ok("Конфигурация", "Файл загружен")
     *         .ok("Команды", "Зарегистрировано: 5")
     *         .skip("Vault", "Плагин не установлен"));
     * }</pre>
     *
     * @param identity идентификатор плагина
     * @param startupSetup заполнение пользовательской части стартового отчёта
     * @return запущенный runtime
     */
    public static PluginRuntime start(
            PluginBanner.Identity identity,
            Consumer<PluginBanner.Data> startupSetup
    ) {
        return new PluginRuntime(identity, startupSetup);
    }

    /** @return идентификатор, с которым был запущен runtime */
    public PluginBanner.Identity identity() {
        return identity;
    }

    /** @return единый логгер этого плагина */
    public PluginLogger log() {
        return logger;
    }

    /**
     * Быстрый доступ к построению красивого структурированного блока.
     *
     * @param title заголовок блока
     * @return новый MBox
     */
    public MBox mBox(String title) {
        return logger.mBox(title);
    }

    /**
     * Возвращает систему обновлений.
     *
     * @return запущенная система обновлений
     * @throws IllegalStateException если GitHub не был настроен
     */
    public PluginUpdateService updates() {
        if (updates == null) {
            throw new IllegalStateException("GitHub updates are not configured");
        }
        return updates;
    }

    /** @return {@code true}, если bStats ID указан и метрики запущены */
    public boolean hasMetrics() {
        return metrics != null;
    }

    /** @return {@code true}, если GitHub указан и система обновлений запущена */
    public boolean hasUpdates() {
        return updates != null;
    }

    /** Лениво создаёт общий асинхронный исполнитель операций с базой данных. */
    public synchronized DatabaseExecutor databaseExecutor() {
        ensureOpen();
        if (databaseExecutor == null) databaseExecutor = new DatabaseExecutor(plugin);
        return databaseExecutor;
    }

    /**
     * Добавляет строковую диаграмму bStats без раскрытия bStats в коде плагина.
     * Если bStats ID не указан, вызов безопасно пропускается.
     */
    public PluginRuntime simplePie(String chartId, Supplier<String> value) {
        ensureOpen();
        if (metrics != null) {
            metrics.simplePie(chartId, value);
        }
        return this;
    }

    /**
     * Немедленно запускает внеочередную проверку обновлений.
     * Если GitHub не настроен, вызов безопасно пропускается.
     */
    public void reload() {
        ensureOpen();
        if (updates != null) {
            updates.checkNow();
        }
    }

    /**
     * Закрывает инфраструктуру и выводит отчёт выключения.
     * Вызывается владельцем плагина вручную из {@code onDisable()}.
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;

        PluginBanner.Data shutdown = new PluginBanner.Data(identity);
        if (updates == null) {
            shutdown.skip("Обновления", "Система не запускалась");
        } else {
            try {
                updates.close();
                shutdown.ok("Обновления", "Планировщик и listener'ы остановлены");
            } catch (Exception exception) {
                logger.error("Не удалось остановить систему обновлений", exception);
                shutdown.warn("Обновления", message(exception));
            }
        }

        if (databaseExecutor != null) {
            try {
                databaseExecutor.close();
                shutdown.ok("База данных", "Исполнитель остановлен");
            } catch (Exception exception) {
                logger.error("Не удалось остановить исполнитель базы данных", exception);
                shutdown.fail("База данных", message(exception));
            }
        } else {
            shutdown.skip("База данных", "Исполнитель не создавался");
        }

        if (metrics == null) {
            shutdown.skip("bStats", "Метрики не запускались");
        } else {
            try {
                metrics.close();
                shutdown.ok("bStats", "Метрики остановлены");
            } catch (Exception exception) {
                logger.error("Не удалось остановить bStats", exception);
                shutdown.warn("bStats", message(exception));
            }
        }
        PluginBanner.broadcastDisable(shutdown);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Plugin runtime is closed");
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
