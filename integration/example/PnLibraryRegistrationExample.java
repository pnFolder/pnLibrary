// Пример: все настройки при регистрации модуля. Файл не входит в сборку, это образец для копирования.
// Всё необязательное можно удалить: достаточно registerModule(ModuleId.of("main")).
package example;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.DenyAction;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;

public final class PnLibraryRegistrationExample implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ModuleContext context;
    private final PluginRegistration registration;

    public PnLibraryRegistrationExample(JavaPlugin plugin) {
        this.plugin = plugin;
        PnLibrary library = PnLibraryProvider.getOrNull();
        if (library == null) {
            throw new IllegalStateException("pnLibrary недоступна");
        }
        // Версия библиотеки: плагин не стартует на старой.
        if (!library.isAtLeastVersion("0.1.0-dev.1")) {
            throw new IllegalStateException("Нужна pnLibrary не ниже 0.1.0-dev.1");
        }
        this.registration = library.getPlugins().register(plugin);

        // Свои тексты ошибок команд (тип ошибки берётся из api.plugin.CommandErrors):
        // registration.onCommandError(SomeCommandError.class, "Нет прав");

        try {
            this.context = registration.registerModule(ModuleId.of("main"), this::configure);
        } catch (RuntimeException failure) {
            registration.close();
            throw failure;
        }
    }

    public ModuleContext getContext() {
        return context;
    }

    private void configure(PluginBuilder setup) {
        // 1. Метаданные: если автоопределение из plugin.yml не подходит.
        setup.metadata(m -> m.name("MyPlugin").version(plugin.getDescription().getVersion()).authors("pnFolder"));

        // 2. Переключатели.
        setup.options(o -> o.placeholderApi(true));   // публиковать плейсхолдеры в PlaceholderAPI

        // 3. Зависимости.
        setup.dependencies(deps -> {
            // Компонент pnFolder.
            deps.product("pnclans-core", p -> p.minimumVersion("1.0.0")
                    .maximumVersionExclusive("2.0.0")
                    .github("pnFolder", "pnClans")
                    .required(true)
                    .downloadPolicy(DownloadPolicy.AUTOMATIC));   // MANUAL | AUTOMATIC | FORCED
            // Чужой плагин.
            deps.plugin("Vault", "1.7", e -> e
                    .downloadPage("https://www.spigotmc.org/resources/vault.34315/")
                    .required(false)
                    .verifyPluginId(true)
                    .verifyVersion(true)
                    .downloadPolicy(DownloadPolicy.MANUAL));
            // Или скачивать самим: .artifact("https://.../Vault.jar", 123456L, "sha256...").downloadPolicy(DownloadPolicy.AUTOMATIC)
        });

        // 4. Обновления плагина.
        setup.updates(u -> u.repository("pnFolder", "MyPlugin")
                .branch("main")                       // или .tag("v1.0.0") / .commit("abc1234")
                .catalogPath(".pnlibrary")            // папка с releases.json
                .supportedApi(1, 1)                   // какой API умеет ЭТА сборка
                .artifact("(?i)^MyPlugin-.*-java21\\.jar$", 21, 24)   // шаблон имени jar, Java от и до
                .artifact("(?i)^MyPlugin-.*-java25\\.jar$", 25)
                // .exactArtifact("MyPlugin.jar", 8)  // точное имя вместо шаблона
                .configurationBackup(plugin.getDataFolder().toPath(), "config.yml", "messages.yml")
                .backup(BackupKind.DATABASE, "Данные", plugin.getDataFolder().toPath(), "data.db"));   // файловая база
        // Базы MySQL/PostgreSQL/MongoDB помечаются в классе настроек: @BackupDatabase(DatabaseKind.MYSQL, "Данные")

        // 5. Скачивание внешних файлов.
        setup.downloads(plugin.getDataFolder().toPath(), d -> d.file("geoip", f -> f
                .url("https://example.com/geoip.mmdb")
                .optional()                                         // без файла плагин всё равно работает
                .policy(DownloadPolicy.AUTOMATIC)                  // MANUAL | AUTOMATIC | FORCED
                .integrity(1_000_000L, "sha256...")
                .destination(DownloadDestination.DATA_FOLDER, "geoip.mmdb")   // или CACHE
                .java(8)));
        // .platform(PlatformType.X) если файл только для одной платформы

        // 6. Диагностика: что попадёт в отчёт поддержки.
        setup.diagnostics(
                plugin.getDataFolder().toPath(),
                DiagnosticContainer.builder("myplugin")
                        .snapshot(() -> {
                            Map<String, Object> values = new HashMap<String, Object>();
                            values.put("version", plugin.getDescription().getVersion());
                            values.put("players", plugin.getServer().getOnlinePlayers().size());
                            return values;
                        })
                        .value("storage", "sqlite")
                        .configuration(DiagnosticConfiguration.builder("config.yml")
                                .exclude("database.host")                                        // не показывать путь целиком
                                .secretKeyRegex("(?i).*(password|token|secret|api[-_]?key).*")   // скрыть значения по имени ключа
                                .redactValueRegex("(?i)^Bearer .+")                              // скрыть значения по виду
                                .build())
                        .configuration("messages.yml")   // без скрытия
                        .build());

        // 7. Метрики.
        setup.metrics(m -> m.enabled(true)
                .bStats(12345)                       // или bStats(платформа, id) / bStats(карта)
                .fastStats("токен")
                .charts(c -> {
                    c.simplePie("storage", () -> "sqlite");
                    c.advancedPie("modes", () -> {
                        Map<String, Integer> modes = new HashMap<String, Integer>();
                        modes.put("a", 3);
                        modes.put("b", 1);
                        return modes;
                    });
                    c.drilldownPie("versions", () -> Collections.singletonMap(
                            "1.21", Collections.singletonMap("paper", 10)));
                    c.singleLineChart("online", () -> plugin.getServer().getOnlinePlayers().size());
                    c.multiLineChart("lines", () -> {
                        Map<String, Integer> lines = new HashMap<String, Integer>();
                        lines.put("x", 1);
                        lines.put("y", 2);
                        return lines;
                    });
                    c.simpleBarChart("bars", () -> Collections.singletonMap("x", 1));
                    c.advancedBarChart("adv", () -> Collections.singletonMap("x", new int[] {1, 2, 3}));
                }));

        // 8. Удалённая политика: разработчик может запретить запуск плагина.
        // Политика это ОДИН .java-файл (до 1 МБ) по https:// или file:, сервер компилирует его своим javac,
        // а на JRE без компилятора библиотека сама скачивает ECJ. Другие расширения библиотека отвергает.
        setup.remotePolicy(RemotePolicySettings.builder()
                .source("https://raw.githubusercontent.com/pnFolder/MyPlugin/main/policy/MyPluginPolicy.java")
                .checkEvery(Duration.ofHours(6))            // не меньше минуты
                .startupTimeout(Duration.ofSeconds(10))
                .onDeny(DenyAction.DISABLE_PLUGIN)          // или DISABLE_MODULE
                .onError(PolicyErrorAction.KEEP_RUNNING)    // DENY | DENY_UNTIL_CHECKED | KEEP_RUNNING
                .value("edition", "free")                   // значения, которые получит код политики
                .build());
    }

    @Override
    public void close() {   // вызвать в onDisable
        registration.close();
    }
}
