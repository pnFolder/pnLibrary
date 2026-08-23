package ru.privatenull.pnlibrary.update;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import ru.privatenull.pnlibrary.banner.PluginBanner;

/**
 * Координирует периодическую проверку, установку и уведомления об обновлении.
 *
 * <p>Сервис создаётся автоматически после {@link PluginBanner#broadcastEnable}
 * и доступен через {@link PluginBanner.Identity#updates()}.</p>
 */
public final class PluginUpdateService implements AutoCloseable {

    private final PluginBanner.Identity identity;
    private final UpdateReporter reporter;
    private final JavaPlugin plugin;
    private final GitHubReleaseClient github;
    private final StagedUpdateInstaller installer;
    private final AdministratorUpdateNotifier notifier;
    private final AtomicBoolean checkRunning = new AtomicBoolean();

    private volatile UpdateSnapshot snapshot = UpdateSnapshot.initial();
    private BukkitTask scheduledTask;
    private volatile boolean closed;

    /** Создаёт сервис. Обычно напрямую вызывать конструктор не требуется. */
    public PluginUpdateService(PluginBanner.Identity identity, UpdateReporter reporter) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        plugin = identity.plugin();
        github = new GitHubReleaseClient(identity);
        installer = new StagedUpdateInstaller(identity, github);
        notifier = new AdministratorUpdateNotifier(identity, () -> snapshot);
    }

    /** Запускает периодическую проверку. Повторный вызов ничего не делает. */
    public synchronized void start() {
        ensureOpen();
        if (scheduledTask != null) return;
        notifier.start();
        schedule();
    }

    /** Немедленно ставит внеочередную проверку в асинхронный планировщик. */
    public void checkNow() {
        ensureOpen();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::performCheck);
    }

    /** Перезапускает периодический планировщик и сразу выполняет новую проверку. */
    public synchronized void restart() {
        ensureOpen();
        cancelScheduledTask();
        schedule();
    }

    /** Вручную уведомляет игрока, если обновление доступно и permission подходит. */
    public void notifyAdministrator(Player player) {
        notifier.notifyPlayer(player);
    }

    /** @return было ли завершено хотя бы одно обращение к GitHub */
    public boolean isCheckCompleted() {
        return snapshot.checkCompleted();
    }

    /** @return доступна ли более новая версия */
    public boolean isUpdateAvailable() {
        return snapshot.updateAvailable();
    }

    /** @return загружен ли проверенный JAR в {@code plugins/update} */
    public boolean isUpdateDownloaded() {
        return snapshot.updateDownloaded();
    }

    /** @return последняя известная версия или {@code null} */
    public String latestVersion() {
        return snapshot.latestVersion();
    }

    /** @return ссылка на JAR или страницу Release; может быть {@code null} */
    public String downloadUrl() {
        UpdateSnapshot current = snapshot;
        return current.downloadUrl() == null ? current.pageUrl() : current.downloadUrl();
    }

    /** @return последняя ошибка проверки/скачивания или {@code null} */
    public String lastError() {
        return snapshot.error();
    }

    /** @return фактическое permission для игровых уведомлений */
    public String notificationPermission() {
        return notifier.permission();
    }

    /** @return был ли сервис окончательно остановлен */
    public boolean isClosed() {
        return closed;
    }

    /** Останавливает планировщик и удаляет listener входа игроков. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelScheduledTask();
        notifier.close();
    }

    private void performCheck() {
        if (closed || !checkRunning.compareAndSet(false, true)) return;
        try {
            UpdateSnapshot previous = snapshot;
            LatestRelease release = github.fetchLatest();
            if (closed) return;
            if (release == null) {
                snapshot = UpdateSnapshot.completedWithoutRelease();
                return;
            }

            SemanticVersion latest = SemanticVersion.parse(release.version());
            SemanticVersion current = SemanticVersion.parse(plugin.getDescription().getVersion());
            if (latest.compareTo(current) <= 0) {
                snapshot = UpdateSnapshot.upToDate(release);
                if (identity.showUpToDateMessage()
                        && (!previous.checkCompleted() || previous.updateAvailable())) {
                    runGlobal(reporter::upToDate);
                }
                return;
            }

            boolean sameVersion = previous.updateAvailable()
                    && release.version().equalsIgnoreCase(previous.latestVersion());
            DownloadResult download = prepareUpdate(previous, release, sameVersion);
            if (closed) return;
            UpdateSnapshot next = UpdateSnapshot.updateAvailable(release, download);
            snapshot = next;

            boolean downloadStateChanged = previous.updateDownloaded() != next.updateDownloaded();
            if (!sameVersion) {
                runGlobal(() -> reporter.updateAvailable(release.version(), release.pageUrl()));
            }
            if (downloadStateChanged && next.updateDownloaded()) {
                runGlobal(() -> reporter.updateDownloaded(release.version(), download.stagedFile()));
            } else if (download.error() != null && identity.showUpdateErrors()) {
                runGlobal(() -> reporter.downloadFailed(download.error()));
            }
            if (!sameVersion || downloadStateChanged) {
                runGlobal(notifier::notifyOnline);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            String reason = message(exception);
            snapshot = snapshot.withError(reason);
            if (identity.showUpdateErrors()) {
                runGlobal(() -> reporter.checkFailed(reason));
            }
        } finally {
            checkRunning.set(false);
        }
    }

    private DownloadResult prepareUpdate(
            UpdateSnapshot previous,
            LatestRelease release,
            boolean sameVersion
        ) {
        if (!identity.autoDownloadUpdates()) return DownloadResult.disabled();
        if (sameVersion && previous.updateDownloaded()) {
            return DownloadResult.success(previous.stagedFile());
        }
        return installer.prepare(release);
    }

    private void schedule() {
        long periodTicks = Math.max(1L,
                (identity.updateCheckInterval().toMillis() + 49L) / 50L);
        scheduledTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::performCheck, 1L, periodTicks);
    }

    private void runGlobal(Runnable action) {
        if (plugin.isEnabled() && !closed) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled() && !closed) action.run();
            });
        }
    }

    private synchronized void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Update service is closed");
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
