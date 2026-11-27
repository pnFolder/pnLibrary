# Конфигурация

Конфигурация описывается обычным классом с значениями по умолчанию. Библиотека сама создаёт YAML-файл,
дописывает недостающие поля, сохраняет комментарии и проверяет значения.

## Модули

| Модуль | Назначение |
| --- | --- |
| `pnlibrary-config-api` (`:modules:features:config-api`) | Типы, аннотации и `ConfigScope`. От него зависят плагины. |
| `pnlibrary-config` (`:modules:features:config-runtime`) | Реализация на SnakeYAML. Входит в сборку pnLibrary. |

Пакет API: `org.pnfolder.pnlibrary.api.config`.

## Минимальный пример

```kotlin
data class Settings(
    @ConfigComment("Сколько денег получает новый игрок.")
    @ConfigRange(min = 0.0)
    var startingBalance: Double = 100.0,

    @ConfigKey("welcome-message")
    @ConfigNotBlank
    var greeting: String = "Добро пожаловать!",
)

val config = context.configs.yaml<Settings>("settings.yml") { Settings() }
val settings = config.load().value   // создаёт файл при первом запуске
```

Дальше: `config.get()` — текущее значение, `config.reload()` — перечитать файл, `config.save()` — записать, `config.update { … }` — изменить и сохранить.

`context.configs` — это `ConfigScope` модуля. Из Java используйте `scope.yaml(path, Settings.class, Settings::new)`.

## Проверка значений

Работают два способа, их проблемы складываются в один `ConfigValidationException`:

1. **Аннотации** на полях: `@ConfigRange`, `@ConfigNotBlank`, `@ConfigPattern`, `@ConfigRequired`. Остальные аннотации: `@ConfigComment`, `@ConfigKey`, `@ConfigIgnore`, `@ConfigAlias`, `@ConfigNewLine`, `@ConfigOrder`, `@ConfigDefaultOnInvalid`, `@ConfigSerializeWith`, `@ConfigPolymorphic`.
2. **Программные правила** для условий, которые аннотациями не выразить:

```kotlin
val validator = ConfigValidatorBuilder<Settings>()
    .require("greeting", "не должно быть пустым") { it.greeting.isNotBlank() }
    .build()

val config = scope.yaml("settings.yml", ConfigOptions.DEFAULT, validator) { Settings() }
```

## Настройки синхронизации (`ConfigOptions`)

| Поле | Что задаёт |
| --- | --- |
| `missingFile` | Что делать, если файла нет (по умолчанию создать). |
| `missingValues` | Что делать с полями модели, которых нет в файле (по умолчанию добавить). |
| `unknownValues` | Что делать с ключами, которых нет в модели (по умолчанию оставить). |
| `comments` | Синхронизация комментариев из кода. |
| `backups` | Делать ли резервную копию перед перезаписью. |
| `migrations` | Версионная цепочка миграций `ConfigMigrationPlan`. По умолчанию `ConfigSteps.NONE`; `withoutVersion()` отключает маркер. |
| `naming` | Преобразование имён полей в ключи YAML: `AS_DECLARED`, `CAMEL_CASE`, `SNAKE_CASE`, `KEBAB_CASE`, `UPPER_SNAKE_CASE`. |

Явное имя ключа задаёт `@ConfigKey` и всегда сильнее `naming`.

## Версия файла и шаги изменений

В каждый файл добавляется маркер версии `_config-version: N` с предупреждением на двух языках. Вручную его
трогать не нужно. Шаги описываются в классе, который наследует `ConfigSteps`:

```kotlin
object ShopSteps : ConfigSteps<ShopConfig>() {
    init {
        version(2) {
            rename("pool-size", "db.pool.size")
        }
        version(3) {
            val address = get("redis-address")
            put("redis.host", address.before(":"))
            put("redis.port", address.after(":").toInt())
            remove("redis-address")
        }
    }
}

val config = scope.yaml("shop.yml", ConfigOptions.builder().migrations(ShopSteps).build()) { ShopConfig() }
```

Действия внутри `version(n) { ... }`:
- `get(путь)` возвращает значение, его можно сохранить в переменную;
- `put(путь, значение)` записывает значение, `remove(путь)` удаляет ключ;
- `rename(откуда, куда)` переименовывает или переносит.

Для значения доступны `before(...)`, `after(...)` и `toInt()`.

Правила:
- номера версий идут подряд, начиная с 2; версия 1 означает состояние до любых шагов;
- старые шаги не меняем, новые дописываем в конец;
- при загрузке применяются только версии новее маркера файла, файл без маркера считается версией 1;
- если шаг не выполнился, файл остаётся без изменений, причина пишется в ошибку;
- файл, созданный более новой версией плагина, открывается для чтения. Сохранение, изменение и сброс такого файла отклоняются.

Без маркера версии: `ConfigOptions.builder().withoutVersion()`.

## Полиморфные типы

Реализацию базового типа публикует билдер. Ничего не публикуется, пока не вызван `register()`:

```kotlin
val registration = module.configs.type<Reward, ItemReward>()
    .name("item")
    .alias("items")
    .priority(10)
    .into("shop/main")          // куда добавляем слово
    .register()
```

Регистрация живёт и умирает вместе с модулем того, кто её сделал. Закрытие `registration` убирает слово.

### Куда добавляется слово (`into`)

Адрес везде один: `ConfigAddress`. Его не надо писать строкой, он собирается из частей (плагин → модуль → файл):

```kotlin
val shop = ConfigAddress.plugin("shop")                          // весь плагин
val main = shop.inModule("main")                                 // один модуль плагина
val rewards = main.inFile("rewards.yml")                         // один файл
val bukkit = main.inFile("bukkit.yml", ConfigRoot.SERVER)        // файл из другого корня

module.configs.type<Reward, ItemReward>().name("item").into(main).register()
```

Что принимает `into` (одно и то же у слов типов и у сериализаторов):

| Что передать | Охват |
| --- | --- |
| ничего | только свои конфиги модуля |
| `PluginId` или `ConfigAddress.plugin("shop")` | все конфиги плагина |
| `ConfigScope` другого модуля (`otherModule.configs`) | все файлы этого модуля |
| `ConfigAddress.plugin("shop").inModule("main")` | один модуль |
| `ManagedConfig` или `ConfigInfo` из каталога | один файл |
| `ConfigAddress…inFile("rewards.yml")` | один файл по пути |
| `.everywhere()` | все файлы всех плагинов |

Строка (`"shop"`, `"shop/main"`, `"shop/main:rewards.yml"`, `"*"`) осталась короткой записью для команд и конфигов; в коде
берите объекты. Найти нужное можно в каталоге: `library.configurations.all()` даёт `ConfigInfo` с `address` и `moduleAddress`,
`library.plugins` даёт список плагинов.

Слово становится понятно хозяину конфига сразу: ожидающие конфиги перечитываются при регистрации. При закрытии типа
перечитывания нет, значение уйдёт при следующем `reload`.

### Защита

- слова, объявленные у базового типа аннотацией `@ConfigTypes`, и слова плагина-хозяина файла занять нельзя;
- `ConfigOptions.unknownTypes`: `FAIL` (ошибка, по умолчанию) или `SKIP`. При `SKIP` элемент списка с незнакомым `type`
  пропускается, но остаётся в файле. Пока есть пропущенные, `save`, `update` и `resetToDefaults` заблокированы, чтобы не
  затереть чужие записи. Появится тип, файл перечитается сам.

### Сериализаторы в чужих файлах

Сериализатор типа можно добавить так же, как слово: по тому же адресу (`*`, плагин, модуль, файл).

```kotlin
// только в этом модуле (как раньше)
module.configs.serializer<Point>(PointSerializer())

// в чужих файлах
val shop = ConfigAddress.plugin("shop")
module.configs.serializerFor(PointSerializer()).into(shop).register()                                   // весь плагин
module.configs.serializerFor(PointSerializer()).into(shop.inModule("main").inFile("places.yml")).register() // один файл
module.configs.serializerFor(PointSerializer()).priority(5).everywhere().register()                     // везде

library.configurations.serializers()      // что добавлено и кем
```

Правила:
- у файла свой модуль главнее: его собственные сериализаторы (и `@ConfigSerializeWith`) не заменяются, добавление закрывает
  только то, для чего у модуля сериализатора нет; среди добавлений выигрывает большее `priority`;
- для чужих файлов нельзя брать примитивы и типы из `java.`, `javax.`, `kotlin.`, `jdk.` (иначе можно подменить чтение обычных
  значений); в своём модуле можно любые;
- закрытие `registration` или модуля убирает сериализатор. Уже прочитанные файлы держат значение до следующего `reload`.

Слово полиморфного типа тоже принимает `*`: `.into("*")` или `.everywhere()`. Слово плагина-хозяина файла при этом не
затеняется: в его файле действует его значение.

## Адреса и каталог конфигов

Каждый файл имеет идентификатор `плагин/модуль:путь`, например `shop/main:rewards.yml`. Разбор: `ConfigId.parse`,
`ConfigAddress.parse` (адрес может быть неполным: `shop`, `shop/main`).

```kotlin
library.configurations.all()                       // List<ConfigInfo>
library.configurations.get(id)                     // ManagedConfig?
library.configurations.find("shop/main")           // List<ConfigInfo> по адресу
library.configurations.byModel(Rewards::class.java) // все файлы с такой моделью
library.configurations.types(Reward::class.java)  // слова полиморфного типа и кто их добавил
library.plugins.requireById("shop").configs()      // файлы плагина
module.configs.list()                              // файлы своего модуля
```

`ConfigInfo` не содержит значений, поэтому секреты через него не утекают.

## Файлы вне папки плагина

`ConfigOptions.builder().root(...)` выбирает корень пути:

| Корень | Где | В ID |
| --- | --- | --- |
| `PLUGIN` | папка плагина (по умолчанию) | `shop/main:rewards.yml` |
| `PLUGINS` | папка `plugins` | `shop/main:@plugins/Other/config.yml` |
| `EXTERNAL` | любой абсолютный путь (`yamlAt`) | `shop/main:@external/D:/data/x.yml` |
| `SERVER` | корень сервера | `shop/main:@server/bukkit.yml` |

Для чужих корней принудительно: файла нет → ошибка (не создаём), резервная копия включена, `REMOVE` заменяется на
`PRESERVE`. Выход за корень и символические ссылки наружу запрещены. Такие файлы не показываются диагностике.

### Файл в любом месте

Если файл лежит где угодно (другой диск, подключённая папка, путь взят из собственного конфига плагина), открывайте его по
абсолютному пути:

```kotlin
val path = Path.of(settings.value.dataFile)            // путь пришёл из вашего основного конфига
val data = module.configs.yamlAt<DataFile>(path) { DataFile() }
```

Это ваш файл: он создаётся, если его нет, и перезаписывается, ограничений чужих корней нет. ID: `плагин/модуль:@external/<путь>`.
Секреты (`@ConfigSecret`) скрываются в отчётах, как и в своей папке. Один физический файл пишет один конфиг, остальные
открывают его с `readOnly()`. Файлы на другой машине (SFTP, HTTP) не поддерживаются, нужна подключённая папка.

`ConfigOptions.readOnly()`: файл только читается, ничего не пишется. Читателей одного файла может быть несколько,
писатель на физический файл только один.

## Правка настроек в игре

Поле, которое администратор может менять из игры, помечается `@ConfigEditable`:

```kotlin
class ShopConfig {
    @field:ConfigEditable("Магазин включён", "Можно ли покупать предметы.")
    var enabled: Boolean = true

    @field:ConfigEditable("Размер пула", "Сколько соединений держать открытыми.", apply = ConfigApply.RESTART)
    @field:ConfigRange(min = 1.0, max = 64.0)
    var poolSize: Int = 10

    @field:ConfigSecret
    var token: String = ""            // секреты в списке никогда не показываются
}
```

Поддерживаются boolean, целые и дробные числа, текст и enum; границы берутся из `@ConfigRange`. Значение проверяется так же,
как при записи файла, и при отказе файл не меняется.

| `apply` | Когда настройка начинает работать |
| --- | --- |
| `LIVE` | сразу, плагин читает значение каждый раз |
| `RELOAD` | после перечитывания: плагин берёт значение в своём обработчике |
| `RESTART` | после перезапуска сервера |

```kotlin
config.settings                          // настройки с текущими значениями
config.edit("pool-size", "20")           // изменить и записать, вернёт старое и новое значение

config.onChanged(Consumer { change -> cache.clear() })            // файл прочитан, записан или изменён
config.onReloadRequired(Consumer { request ->                     // изменили настройку с RELOAD или RESTART
    if (request.apply == ConfigApply.RELOAD) pool.resize(config.value.poolSize)
})
```

В обе функции можно передать несколько обработчиков; ошибка одного не мешает остальным. Пока изменения не заработали,
`ConfigInfo.pendingApply` показывает, чего они ждут; чтение файла снимает `RELOAD`, перезапуск — `RESTART`.

В игре: `/pn gui` → «Конфигурации» (переключатель — клик, вариант — клик по кругу, число — левый и правый клик, текст — вводится
в чате) или команды `/pn configs settings <id>` и `/pn configs edit <id> <путь> <значение>`. Менять может тот, у кого есть право
`pnlibrary.configs.edit`.

## Команда `/pn configs`

Право `pnlibrary.configs`, консоль проходит всегда. Значения из файлов команда не показывает.

| Команда | Что делает |
| --- | --- |
| `/pn configs [страница]` | список файлов |
| `/pn configs info <id>` | где лежит, загружен ли, что пропущено, проблемы |
| `/pn configs reload <id>` | перечитать; при ошибке остаётся прежнее значение |
| `/pn configs validate <id>` | проверка без записи |
| `/pn configs types <тип>` | слова полиморфного типа и кто их добавил |
| `/pn configs settings <id>` | настройки файла, которые можно менять в игре |
| `/pn configs edit <id> <путь> <значение>` | изменить настройку и сохранить файл (право `pnlibrary.configs.edit`) |

## Секреты и отчёты поддержки

Поле с паролем, токеном или вебхуком помечается `@field:ConfigSecret`: его значение скрывается в отчёте `/pn debug`,
отдельной записи в настройках библиотеки не нужно. Остальное из настроек библиотеки: `privacy` (по умолчанию выключено), см. [settings.md](settings.md).

## Резервные копии баз данных

Если плагин хранит данные в MySQL/MariaDB, PostgreSQL или MongoDB, их можно копировать перед обновлением плагина и
возвращать при откате. Это описывается аннотациями (`DatabaseKind`: `MYSQL`, `MONGODB`, `POSTGRESQL`):

- `@BackupDatabase(kind, name, tables)` на поле с именем базы (или на поле-секции подключения, или на классе
  конфигурации); повторяемая, имена баз в плагине различаются. В `tables` допустима маска `*` (`"log_*"`), пусто — все.
- `@DatabaseField(role, database)` связывает поля `host`, `port`, `database`, `user`, `password` с базой, если их имена
  нестандартные (`DatabaseRole`). Пароль помечайте `@ConfigSecret`.
- `@BackupTable(database)` на поле с именем таблицы.

```kotlin
@field:BackupDatabase(DatabaseKind.POSTGRESQL, "Clans", tables = ["clans", "clan_members"])
var clansDb = "clans"
@field:DatabaseField(DatabaseRole.HOST, database = "Clans")
var clansHost = "pg.local"
```

Копируются только таблицы (коллекции в MongoDB); триггеры, процедуры и представления нет. Подробности и меню
копий: [updates.md](updates.md).
