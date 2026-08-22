# pnLibrary

Общая встраиваемая библиотека для плагинов экосистемы PrivateNull.

Сейчас предоставляет:

- единый banner включения и выключения;
- асинхронную проверку обновлений через GitHub;
- единое уведомление об обновлении в консоли и для персонала с заданным permission;
- кликабельные ссылки, title и звук для уведомлений об обновлении;
- единый парсер обычных `&`/`§`-цветов, RGB и RGBA без MiniMessage;
- единый публичный маршрутизатор SQLite, MySQL, MongoDB и Redis;
- HikariCP-пул для JDBC и версионные миграции схемы;
- один управляемый пул/клиент на плагин и безопасное закрытие всех ресурсов;
- сравнение версий;
- публичную локализацию предметов, блоков, чар и зелий Minecraft на русском и английском;
- общую абстракцию экономики для Vault и PlayerPoints;
- анимацию открытия GUI: предметы и заголовок появляются постепенно.

## Единый lifecycle плагина

`PluginRuntime` заменяет отдельную настройку updater, bStats, join-уведомлений и
banner. GitHub-репозиторий берётся из `website` в `plugin.yml`; если ссылки нет,
используется `Dy6HiLa/<plugin-name>`. Permission выбирается из объявленных
`.admin`/`.update`, проверка обновлений выполняется библиотекой каждые шесть часов.

```java
private PluginRuntime pnRuntime;

@Override
public void onEnable() {
    pnRuntime = PluginRuntime.start(this)
            .simplePie("database_type", () -> database.type().name());
}

@Override
public void onDisable() {
    if (pnRuntime != null) pnRuntime.close();
}
```

ID проекта bStats указывается один раз как `bstats-id` в `plugin.yml`. В коде
плагина больше не нужны `BSTATS_PLUGIN_ID`, `GITHUB_REPOSITORY`, период updater,
Discord URL, ручной `PlayerJoinEvent`, прямой `Metrics` и отдельный banner.

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
