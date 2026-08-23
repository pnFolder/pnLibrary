package ru.privatenull.pnlibrary.banner;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import ru.privatenull.pnlibrary.update.PluginUpdateService;
import ru.privatenull.pnlibrary.update.UpdateReporter;
import ru.privatenull.pnlibrary.logging.MBox;
import ru.privatenull.pnlibrary.logging.PluginLogger;

/**
 * Главная точка входа для баннеров запуска и выключения плагина.
 *
 * <p>Класс хранит только публичный API. Формирование сообщений, обращение к
 * GitHub, скачивание обновлений и сравнение версий вынесены во внутренние
 * классы пакета.</p>
 */
public final class PluginBanner {

    private PluginBanner() {
    }

    /** Состояние компонента, отображаемое в отчёте запуска или выключения. */
    public enum Status {
        OK("OK", ChatColor.GREEN),
        WARN("WARN", ChatColor.GOLD),
        FAIL("FAIL", ChatColor.RED),
        SKIP("SKIP", ChatColor.GRAY);

        private final String displayName;
        private final ChatColor color;

        Status(String displayName, ChatColor color) {
            this.displayName = displayName;
            this.color = color;
        }

        /** @return короткое название статуса для консоли */
        public String displayName() {
            return displayName;
        }

        /** @return цвет статуса в консоли Bukkit */
        public ChatColor color() {
            return color;
        }
    }

    /**
     * Состояние компонента и необязательная строка с пояснением.
     *
     * @param status состояние компонента
     * @param details пояснение или {@code null}
     */
    public record Entry(Status status, String details) {

        public Entry {
            status = Objects.requireNonNull(status, "status");
            details = details == null || details.isBlank() ? null : details.trim();
        }

        public Entry(Status status) {
            this(status, null);
        }
    }

    /**
     * GitHub-репозиторий, используемый для проверки обновлений.
     *
     * @param owner владелец репозитория
     * @param repository название репозитория
     */
    public record GitHubRepository(String owner, String repository) {

        private static final String SEGMENT_PATTERN = "[A-Za-z0-9_.-]+";

        public GitHubRepository {
            owner = validateSegment(owner, "owner");
            repository = validateSegment(repository, "repository");
        }

        /** @return URL метода GitHub API для получения последнего Release */
        public String apiUrl() {
            return "https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest";
        }

        /** @return URL страницы последнего Release */
        public String releasesUrl() {
            return "https://github.com/" + owner + "/" + repository + "/releases/latest";
        }

        private static String validateSegment(String value, String field) {
            if (value == null || !value.trim().matches(SEGMENT_PATTERN)) {
                throw new IllegalArgumentException("Invalid GitHub " + field + ": " + value);
            }
            return value.trim();
        }
    }

    /**
     * Постоянный идентификатор плагина.
     *
     * <p>Создаётся один раз и затем передаётся в отдельные {@link Data} для
     * запуска, выключения или другого отчёта. Здесь находятся сведения,
     * одинаковые на протяжении всей работы плагина: экземпляр плагина,
     * название команды-разработчика и параметры обновлений.</p>
     */
    public static final class Identity {

        private static final int DEFAULT_MAX_UPDATE_SIZE_MB = 100;
        private static final String DEFAULT_ASSET_PATTERN =
                "(?i)^(?!.*(?:sources|javadoc)).*\\.jar$";

        private final JavaPlugin plugin;
        private final String developer;
        private final PluginLogger logger;
        private GitHubRepository github;
        private boolean showUpToDateMessage;
        private boolean showUpdateErrors = true;
        private boolean autoDownloadUpdates;
        private boolean notifyAdministrators;
        private boolean notifyOnlineAdministrators = true;
        private boolean notifyAdministratorsOnJoin = true;
        private String notificationPermission;
        private String supportUrl;
        private Integer bStatsPluginId;
        private Duration updateTimeout = Duration.ofSeconds(15);
        private Duration updateCheckInterval = Duration.ofHours(6);
        private long maxUpdateSizeBytes = DEFAULT_MAX_UPDATE_SIZE_MB * 1024L * 1024L;
        private Pattern updateAssetPattern = Pattern.compile(DEFAULT_ASSET_PATTERN);
        private PluginUpdateService updateService;

        /**
         * Создаёт идентификатор плагина.
         *
         * @param plugin экземпляр Bukkit/Paper-плагина
         * @param developer отображаемое название автора, команды или бренда
         */
        public Identity(JavaPlugin plugin, String developer) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.developer = requireText(developer, "developer");
            logger = new PluginLogger(this);
        }

        /**
         * Включает проверку последнего GitHub Release.
         *
         * @param owner владелец репозитория
         * @param repository название репозитория
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity github(String owner, String repository) {
            github = new GitHubRepository(owner, repository);
            return this;
        }

        /**
         * Задаёт ID проекта bStats. Значение хранится только в идентификаторе и
         * используется {@code PluginRuntime}; {@code plugin.yml} не читается.
         *
         * @param pluginId положительный ID проекта на bStats
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity bStats(int pluginId) {
            if (pluginId <= 0) {
                throw new IllegalArgumentException("bStats plugin id must be positive");
            }
            bStatsPluginId = pluginId;
            return this;
        }

        /**
         * Управляет сообщением об уже установленной актуальной версии.
         * По умолчанию такое сообщение не показывается.
         *
         * @param value {@code true}, чтобы выводить сообщение об актуальной версии
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity showUpToDateMessage(boolean value) {
            showUpToDateMessage = value;
            return this;
        }

        /**
         * Устаревшее название {@link #showUpToDateMessage(boolean)}.
         *
         * @param value показывать ли сообщение об актуальной версии
         * @return этот идентификатор для цепочки вызовов
         */
        @Deprecated(forRemoval = false)
        public Identity notifyUpToDate(boolean value) {
            return showUpToDateMessage(value);
        }

        /**
         * Управляет выводом ошибок GitHub и скачивания в консоль.
         * По умолчанию ошибки показываются.
         *
         * @param value {@code true}, чтобы показывать ошибки проверки обновлений
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity showUpdateErrors(boolean value) {
            showUpdateErrors = value;
            return this;
        }

        /**
         * Включает автоматическое скачивание новой версии.
         *
         * <p>Обновление помещается в серверную папку {@code plugins/update} и
         * применяется при следующем запуске сервера. Работающий JAR не удаляется
         * и не изменяется.</p>
         *
         * @param value {@code true}, чтобы автоматически скачивать новый JAR
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity autoDownloadUpdates(boolean value) {
            autoDownloadUpdates = value;
            return this;
        }

        /**
         * Включает или выключает уведомления игроков-администраторов.
         * По умолчанию уведомления выключены.
         *
         * @param value отправлять ли сообщения администраторам
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity notifyAdministrators(boolean value) {
            notifyAdministrators = value;
            return this;
        }

        /**
         * Управляет уведомлением администраторов, уже находящихся на сервере,
         * сразу после обнаружения новой версии.
         *
         * @param value уведомлять ли администраторов онлайн
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity notifyOnlineAdministrators(boolean value) {
            notifyOnlineAdministrators = value;
            return this;
        }

        /**
         * Управляет уведомлением администратора при входе на сервер.
         * Один игрок получает сообщение не более одного раза для каждой версии.
         *
         * @param value уведомлять ли администратора при входе
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity notifyAdministratorsOnJoin(boolean value) {
            notifyAdministratorsOnJoin = value;
            return this;
        }

        /**
         * Задаёт permission для получения уведомлений.
         * Permission обязательно задаётся явно, если уведомления включены.
         *
         * @param permission Bukkit permission
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity notificationPermission(String permission) {
            notificationPermission = requireText(permission, "notification permission");
            return this;
        }

        /**
         * Добавляет в сообщение администратора кнопку поддержки.
         *
         * @param url полный HTTP(S)-адрес страницы поддержки
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity supportUrl(String url) {
            String checkedUrl = requireText(url, "support URL");
            if (!checkedUrl.matches("https?://.+")) {
                throw new IllegalArgumentException("support URL must use HTTP or HTTPS");
            }
            supportUrl = checkedUrl;
            return this;
        }

        /**
         * Задаёт таймаут одного HTTP-запроса при проверке и скачивании.
         *
         * @param timeout положительная продолжительность не более двух минут
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity updateTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
                throw new IllegalArgumentException("update timeout must be between 1 ns and 2 minutes");
            }
            updateTimeout = timeout;
            return this;
        }

        /**
         * Задаёт интервал фоновой проверки GitHub.
         * По умолчанию обновления проверяются каждые шесть часов.
         *
         * @param interval интервал от одной минуты до семи дней
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity updateCheckInterval(Duration interval) {
            Objects.requireNonNull(interval, "interval");
            if (interval.compareTo(Duration.ofMinutes(1)) < 0
                    || interval.compareTo(Duration.ofDays(7)) > 0) {
                throw new IllegalArgumentException("update interval must be between 1 minute and 7 days");
            }
            updateCheckInterval = interval;
            return this;
        }

        /**
         * Ограничивает размер автоматически скачиваемого JAR.
         * Значение по умолчанию — 100 МБ.
         *
         * @param megabytes максимальный размер от 1 до 1024 МБ
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity maxUpdateSizeMegabytes(int megabytes) {
            if (megabytes < 1 || megabytes > 1024) {
                throw new IllegalArgumentException("max update size must be between 1 and 1024 MB");
            }
            maxUpdateSizeBytes = megabytes * 1024L * 1024L;
            return this;
        }

        /**
         * Задаёт регулярное выражение для выбора файла из GitHub Release.
         * Выражение проверяется по имени asset целиком. По умолчанию выбирается
         * первый JAR, в названии которого нет {@code sources} или {@code javadoc}.
         *
         * @param regex регулярное выражение имени файла
         * @return этот идентификатор для цепочки вызовов
         */
        public Identity updateAssetPattern(String regex) {
            updateAssetPattern = Pattern.compile(requireText(regex, "update asset pattern"));
            return this;
        }

        /** @return экземпляр плагина, которому принадлежит баннер */
        public JavaPlugin plugin() {
            return plugin;
        }

        /** @return отображаемое название автора, команды или бренда */
        public String developer() {
            return developer;
        }

        /** @return единый логгер, доступный ещё до запуска {@code PluginRuntime} */
        public PluginLogger log() {
            return logger;
        }

        /**
         * Создаёт красивый структурированный блок для консоли.
         *
         * @param title заголовок блока
         * @return новый MBox
         */
        public MBox mBox(String title) {
            return logger.mBox(title);
        }

        /** @return GitHub-репозиторий или {@code null}, если проверка отключена */
        public GitHubRepository github() {
            return github;
        }

        /** @return показывается ли сообщение об актуальной версии */
        public boolean showUpToDateMessage() {
            return showUpToDateMessage;
        }

        /** @return показываются ли ошибки проверки и скачивания */
        public boolean showUpdateErrors() {
            return showUpdateErrors;
        }

        /** @return включено ли автоматическое скачивание обновления */
        public boolean autoDownloadUpdates() {
            return autoDownloadUpdates;
        }

        /** @return включены ли уведомления игроков-администраторов */
        public boolean notifyAdministrators() {
            return notifyAdministrators;
        }

        /** @return нужно ли уведомлять администраторов онлайн */
        public boolean notifyOnlineAdministrators() {
            return notifyOnlineAdministrators;
        }

        /** @return нужно ли уведомлять администратора при входе */
        public boolean notifyAdministratorsOnJoin() {
            return notifyAdministratorsOnJoin;
        }

        /** @return явно заданное permission или {@code null} */
        public String notificationPermission() {
            return notificationPermission;
        }

        /** @return адрес поддержки или {@code null} */
        public String supportUrl() {
            return supportUrl;
        }

        /** @return явно заданный ID bStats или {@code null} */
        public Integer bStatsPluginId() {
            return bStatsPluginId;
        }

        /** @return таймаут одного HTTP-запроса */
        public Duration updateTimeout() {
            return updateTimeout;
        }

        /** @return интервал фоновой проверки GitHub */
        public Duration updateCheckInterval() {
            return updateCheckInterval;
        }

        /** @return максимальный размер скачиваемого файла в байтах */
        public long maxUpdateSizeBytes() {
            return maxUpdateSizeBytes;
        }

        /** @return шаблон имени GitHub asset, который считается обновлением */
        public Pattern updateAssetPattern() {
            return updateAssetPattern;
        }

        /**
         * Возвращает запущенную систему обновлений.
         *
         * @return сервис обновлений
         * @throws IllegalStateException если GitHub не настроен или баннер запуска ещё не вызван
         */
        public synchronized PluginUpdateService updates() {
            if (updateService == null) {
                throw new IllegalStateException("Update service has not been started");
            }
            return updateService;
        }

        synchronized PluginUpdateService startUpdates(UpdateReporter reporter) {
            if (notifyAdministrators && notificationPermission == null) {
                throw new IllegalStateException(
                        "notification permission must be configured when administrator notifications are enabled");
            }
            if (updateService == null || updateService.isClosed()) {
                PluginUpdateService candidate = new PluginUpdateService(this, reporter);
                try {
                    candidate.start();
                    updateService = candidate;
                } catch (RuntimeException | Error exception) {
                    candidate.close();
                    throw exception;
                }
            }
            return updateService;
        }

        synchronized void stopUpdates() {
            if (updateService != null) {
                updateService.close();
                updateService = null;
            }
        }
    }

    /** Отдельный отчёт одного этапа: например, запуска или выключения. */
    public static final class Data {

        private final Identity identity;
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Map<String, Entry> entriesView = Collections.unmodifiableMap(entries);

        /**
         * Создаёт новый отчёт на основе постоянного идентификатора.
         *
         * @param identity идентификатор, созданный при запуске плагина
         */
        public Data(Identity identity) {
            this.identity = Objects.requireNonNull(identity, "identity");
        }

        /** Упрощённый конструктор для случаев, когда общий идентификатор не нужен. */
        public Data(JavaPlugin plugin, String developer) {
            this(new Identity(plugin, developer));
        }

        /** Упрощённая настройка GitHub; значение сохраняется в идентификаторе. */
        public Data github(String owner, String repository) {
            identity.github(owner, repository);
            return this;
        }

        /** Устаревшее название настройки сообщения об актуальной версии. */
        @Deprecated(forRemoval = false)
        public Data notifyUpToDate(boolean value) {
            identity.notifyUpToDate(value);
            return this;
        }

        /** Настраивает сообщение об актуальной версии через общий идентификатор. */
        public Data showUpToDateMessage(boolean value) {
            identity.showUpToDateMessage(value);
            return this;
        }

        /** Добавляет компонент с произвольным статусом без пояснения. */
        public Data component(String component, Status status) {
            return component(component, status, null);
        }

        /** Добавляет компонент с произвольным статусом и пояснением. */
        public Data component(String component, Status status, String details) {
            entries.put(requireText(component, "component"), new Entry(status, details));
            return this;
        }

        /** Добавляет успешно обработанный компонент. */
        public Data ok(String component) {
            return component(component, Status.OK);
        }

        /** Добавляет успешно обработанный компонент с пояснением. */
        public Data ok(String component, String details) {
            return component(component, Status.OK, details);
        }

        /** Добавляет компонент с некритичным предупреждением. */
        public Data warn(String component) {
            return component(component, Status.WARN);
        }

        /** Добавляет компонент с предупреждением и пояснением. */
        public Data warn(String component, String details) {
            return component(component, Status.WARN, details);
        }

        /** Добавляет компонент с критической ошибкой. */
        public Data fail(String component) {
            return component(component, Status.FAIL);
        }

        /** Добавляет компонент с критической ошибкой и пояснением. */
        public Data fail(String component, String details) {
            return component(component, Status.FAIL, details);
        }

        /** Добавляет пропущенный компонент. Пропуск сам по себе не считается ошибкой. */
        public Data skip(String component) {
            return component(component, Status.SKIP);
        }

        /** Добавляет пропущенный компонент с пояснением. */
        public Data skip(String component, String details) {
            return component(component, Status.SKIP, details);
        }

        /** @return экземпляр плагина из общего идентификатора */
        public JavaPlugin plugin() {
            return identity.plugin();
        }

        /** @return название разработчика из общего идентификатора */
        public String developer() {
            return identity.developer();
        }

        /** @return общий идентификатор этого отчёта */
        public Identity identity() {
            return identity;
        }

        /** @return неизменяемое представление компонентов в порядке добавления */
        public Map<String, Entry> entries() {
            return entriesView;
        }

        /** @return GitHub-репозиторий из общего идентификатора */
        public GitHubRepository github() {
            return identity.github();
        }

        boolean notifyUpToDate() {
            return identity.showUpToDateMessage();
        }

        Status overallStatus() {
            if (contains(Status.FAIL)) return Status.FAIL;
            if (contains(Status.WARN)) return Status.WARN;
            return Status.OK;
        }

        private boolean contains(Status status) {
            return entries.values().stream().anyMatch(entry -> entry.status() == status);
        }
    }

    /**
     * Выводит отчёт запуска и асинхронно проверяет GitHub, если он настроен.
     *
     * @param data данные именно этого запуска
     */
    public static void broadcastEnable(Data data) {
        Data checkedData = Objects.requireNonNull(data, "data");
        BannerRenderer renderer = new BannerRenderer(checkedData);
        renderer.enable();

        if (checkedData.github() != null) {
            checkedData.identity().startUpdates(new BannerUpdateReporter(renderer));
        }
    }

    /**
     * Выводит самостоятельный отчёт выключения.
     * Метод вызывается владельцем плагина вручную из {@code onDisable()}.
     *
     * @param data новые данные, описывающие процесс выключения
     */
    public static void broadcastDisable(Data data) {
        Data checkedData = Objects.requireNonNull(data, "data");
        checkedData.identity().stopUpdates();
        new BannerRenderer(checkedData).disable();
    }

    /**
     * Выводит отдельный баннер критической ошибки.
     *
     * @param data данные текущего этапа
     * @param message понятное описание ошибки
     */
    public static void broadcastError(Data data, String message) {
        new BannerRenderer(Objects.requireNonNull(data, "data"))
                .error(requireText(message, "message"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
