# pnLibrary

Общая встраиваемая библиотека для плагинов экосистемы PrivateNull.

## Требования и сборка

- JDK 25;
- Paper API 26.2 для Bukkit/Paper-модулей;
- BungeeCord API 1.21 или Velocity API 3.3 для соответствующего модуля метрик.

Проект собирается включённым Gradle Wrapper, поэтому отдельная установка Gradle
не нужна. На Windows запустите `gradlew.bat clean test jar`, на Linux/macOS —
`./gradlew clean test jar`. Готовый JAR появится в `build/libs`.

Сейчас предоставляет:

- единый banner включения и выключения;
- единый bStats lifecycle для Bukkit/Paper, BungeeCord и Velocity;
- асинхронную проверку обновлений через GitHub;
- единое уведомление об обновлении в консоли и для персонала с заданным permission;
- кликабельные ссылки, title и звук для уведомлений об обновлении;
- единый парсер MiniMessage, обычных `&`/`§`-цветов, RGB и RGBA;
- единый публичный маршрутизатор SQLite, MySQL, MongoDB и Redis;
- HikariCP-пул для JDBC и версионные миграции схемы;
- один управляемый пул/клиент на плагин и безопасное закрытие всех ресурсов;
- сравнение версий;
- публичную локализацию предметов, блоков, чар и зелий Minecraft на русском и английском;
- общую абстракцию экономики для Vault и PlayerPoints;
- анимацию открытия GUI: предметы и заголовок появляются постепенно.

## Единый lifecycle плагина

`PluginRuntime` объединяет bStats, базовую инфраструктуру, lifecycle-баннер и
новую систему обновлений. Вся конфигурация задаётся явно через один
`PluginBanner.Identity`: библиотека не читает `website`, `bstats-id` или
permissions из `plugin.yml` и не подставляет скрытые значения.

GitHub и bStats опциональны. Если не вызвать `github(...)` или `bStats(...)`,
runtime продолжит запуск и покажет для соответствующего модуля статус `SKIP`.

```java
private PluginRuntime pnRuntime;

@Override
public void onEnable() {
    PluginBanner.Identity identity = new PluginBanner.Identity(this, "PnFolder")
            .github("owner", "repository")
            .bStats(12345)
            .autoDownloadUpdates(true)
            .notifyAdministrators(true)
            .notificationPermission("myplugin.admin")
            .notifyOnlineAdministrators(true)
            .notifyAdministratorsOnJoin(true)
            .supportUrl("https://example.com/support");

    pnRuntime = PluginRuntime.start(identity)
            .simplePie("database_type", () -> database.type().name());
}

@Override
public void onDisable() {
    if (pnRuntime != null) pnRuntime.close();
}
```

В `plugin.yml` остаётся только само объявление permission с нужным `default`.
GitHub, bStats ID, permission уведомлений, ссылка поддержки, интервалы и режим
автоскачивания принадлежат `Identity`.

При включённом `autoDownloadUpdates` новый JAR скачивается в `plugins/update`
под именем работающего плагина. Перед сохранением проверяются ограничение
размера, структура JAR и наличие `plugin.yml`/`paper-plugin.yml`. Bukkit/Paper
заменяет старый JAR подготовленным при следующем **полном перезапуске** сервера;
`/reload` для применения обновления использовать не следует.

Администратор с выбранным permission получает кликабельное уведомление сразу,
если он онлайн, либо при следующем входе. Для каждой новой версии уведомление
отправляется один раз. Фактическое право можно посмотреть через
`pnRuntime.updates().notificationPermission()`.

## Метрики на трёх платформах

`PluginMetrics` выбирает корректную реализацию bStats фабричным методом. В API
нет локального флага `enabled`, поэтому конфигурация самого плагина не может
случайно выключить сбор. Штатный глобальный opt-out bStats при этом сохраняется.

```java
// Bukkit/Paper
metrics = PluginMetrics.bukkit(this, 12345);

// BungeeCord
metrics = PluginMetrics.bungeeCord(this, 12345);

// Velocity: Metrics.Factory внедряется самой Velocity
metrics = PluginMetrics.velocity(this, metricsFactory, 12345);

metrics.simplePie("mode", () -> "production");
```

Во всех трёх случаях владелец плагина вручную вызывает
`metrics.close()` в `onDisable()` или обработчике `ProxyShutdownEvent`.
Повторное закрытие безопасно. Оно останавливает внутренний планировщик bStats;
никакой автоматический hook выключения библиотека не регистрирует.

Полные русские примеры находятся в JavaDoc пакета
`ru.privatenull.pnlibrary.metrics`.

## Единый логгер и MBox

После запуска логгер сразу доступен через `runtime.log()`. До создания runtime
его можно получить через `identity.log()`.

```java
runtime = PluginRuntime.start(identity, startup -> startup
        .ok("Конфигурация", "Файл загружен")
        .ok("Команды", "Зарегистрировано: 5")
        .skip("Vault", "Плагин не установлен"));

runtime.log().info("Загрузка данных");
runtime.log().success("Данные загружены");
runtime.log().warn("Vault не найден");
runtime.log().error("Ошибка базы данных", exception); // плюс полный stack trace

runtime.mBox("Инициализация модулей")
        .ok("Конфигурация", "Файл загружен")
        .ok("Команды", "Зарегистрировано: 5")
        .skip("Discord", "Интеграция отключена")
        .fail("База данных", exception)
        .show();
```

`MBox` сохраняет порядок строк и сам вычисляет итоговое состояние. Исключение,
переданное в `fail`, выводится понятной строкой в блоке и полным stack trace в
штатном серверном логе.

## Экономика

`EconomyService` даёт одинаковый публичный интерфейс для Vault и PlayerPoints.
Интеграции опциональны: если нужного плагина или Vault-провайдера нет, объект
валюты остаётся безопасным, а `available()` возвращает `false`.

```java
import ru.privatenull.pnlibrary.economy.EconomyService;

private EconomyService economies;

@Override
public void onEnable() {
    economies = EconomyService.create(this);
}

public boolean buy(Player player, double price) {
    EconomyService.Currency money = economies.vault();
    return money.available()
            && money.has(player, price)
            && money.withdraw(player, price);
}

public boolean givePoints(Player player, int amount) {
    return economies.playerPoints().deposit(player, amount);
}
```

Обе валюты поддерживают `balance`, `has`, `withdraw`, `deposit` и `format`.
Также доступен поиск через `economies.find("vault")` или
`economies.find("points")`. PlayerPoints принимает только целые положительные
значения, Vault — положительные конечные `double`.

## Маршрутизатор баз данных

`DatabaseRouter` — общедоступная точка подключения для любого плагина. На один
экземпляр плагина создаётся один router: он открывает только выбранный backend,
владеет единственным HikariCP-пулом, `MongoClient` или `JedisPooled` и закрывается
в `onDisable`. Репозитории не создают собственные подключения.

```java
import ru.privatenull.pnlibrary.database.DatabaseRouter;

public final class ExamplePlugin extends JavaPlugin {
    private DatabaseRouter databases;

    @Override
    public void onEnable() {
        databases = DatabaseRouter.from(
                getConfig().getConfigurationSection("storage"), getDataFolder());

        UserRepository users = databases.route(
                jdbc -> new JdbcUserRepository(jdbc),
                mongo -> new MongoUserRepository(mongo.collection("users")),
                redis -> new RedisUserRepository(redis.client(), redis.key("users"))
        );
    }

    @Override
    public void onDisable() {
        if (databases != null) databases.close();
    }
}
```

```yml
storage:
  type: sqlite # sqlite, mysql, mongodb или redis
  sqlite:
    file: data.db
    connection-timeout-ms: 10000
  mysql:
    url: "" # либо jdbc:mysql://host:3306/database
    host: localhost
    port: 3306
    database: plugin
    username: root
    password: ""
    pool-size: 10
  mongodb:
    uri: mongodb://localhost:27017
    database: plugin
    collection: data
  redis:
    uri: redis://localhost:6379/0
    namespace: plugin
```

SQLite принудительно использует пул размером `1`; поэтому JDBC-репозитории должны
брать connection на время одной операции через `jdbc.connection()` и сразу
возвращать его через try-with-resources. Для MongoDB используйте
`mongo.collection(suffix)`, для Redis — `redis.key(suffix)`: namespace будет
добавлен библиотекой без копирования этой логики по плагинам.

## Локализация предметов

pnLibrary содержит официальные таблицы `ru_ru` и `en_us` из Minecraft 1.21.11.
Сервис работает со всеми `Material`, доступными на запущенной версии Paper, и
предоставляет стабильные ключи для обычных предметов и вариантов зелий.

```java
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.pnlibrary.localization.MinecraftLocale;

ItemLocalization items = ItemLocalization.load(MinecraftLocale.RU_RU);

String stone = items.getMaterialName(Material.STONE); // Камень
String displayName = items.getPlainName(itemStack);
Material material = items.matchMaterial("алмазный меч");
Map<Material, String> allRussianMaterials = items.materialNames();
```

Для английского языка используйте `MinecraftLocale.EN_US` или
`ItemLocalization.load("en_us")`. Экземпляр неизменяемый, поэтому его можно
создать при запуске плагина и безопасно переиспользовать до перезагрузки конфигурации.
Загруженные таблицы кэшируются по локали и не требуют повторного чтения ресурсов
при повторном включении плагина.

## Предметы и визуальные сущности

`ItemFactory` читает предметы из Bukkit `ConfigurationSection` или `Map`,
сохраняет точный `ItemStack`, применяет имя, lore, чары и Base64-головы.
`HeadUtil.normalizeTexture(...)` является общей точкой проверки Base64, ссылки
`textures.minecraft.net` и хеша текстуры. `VisualEntity` предоставляет общий
armor-stand fallback для предмета, блока и текста на старых версиях сервера.

## Анимация GUI

```java
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiAnimationType;
import ru.privatenull.pnlibrary.gui.GuiAnimationProfile;

GuiOpenAnimationService guiAnimations = new GuiOpenAnimationService(this);
guiAnimations.open(player, inventory);

GuiAnimationProfile profile = new GuiAnimationProfile(
        GuiAnimationType.CENTER_OUT,
        GuiAnimationType.RIGHT_TO_LEFT, List.of(0, 3, 9, 12),
        GuiAnimationType.LEFT_TO_RIGHT, List.of(5, 8, 14, 17),
        GuiAnimationType.CENTER_OUT);
guiAnimations.open(player, nextInventory, true, profile, clickedSlot);

// При выключении плагина:
guiAnimations.shutdown();
```

Сервис не зависит от pnCases: передайте свой `Plugin`, игрока и уже заполненный
`Inventory`. Доступны `CENTER_OUT`, `LEFT_TO_RIGHT`,
`RIGHT_TO_LEFT`, `TOP_TO_BOTTOM`, `BOTTOM_TO_TOP`, `DIAGONAL_DOWN`,
`DIAGONAL_UP` и `NONE`. `GuiAnimationProfile` связывает эффекты с произвольными
группами слотов левого и правого отсеков; номера слотов библиотека не навязывает.
На поддерживаемых версиях Minecraft также анимируется заголовок окна. Цвета
`&`, `§`, `&#RRGGBB`, `&#RRGGBBAA` и `§x§R§R§G§G§B§B` сохраняются при
ProtocolLib-обновлении заголовка. Minecraft не отображает прозрачность текста,
поэтому в RGBA alpha поглощается, а видимый цвет определяется RGB-компонентом.

## Обновление открытого GUI

```java
import ru.privatenull.pnlibrary.gui.GuiUpdateService;

GuiUpdateService guiUpdates = new GuiUpdateService();
guiUpdates.setTopSlot(player, 13, item);
```

Обновляется только указанный слот открытого меню, без переоткрытия и мигания.
Сервис использует Bukkit-отправку корректного контейнерного пакета, поэтому
безопасно работает и на серверах с ProtocolLib.

`pnLibrary` должна попадать внутрь JAR плагина через Shadow с relocation. Отдельный
`pnLibrary.jar` на Minecraft-сервер устанавливать не требуется.
