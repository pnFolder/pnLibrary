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
- файл, созданный более новой версией плагина, читается, но не перезаписывается.

Без маркера версии: `ConfigOptions.builder().withoutVersion()`.

## Полиморфные типы

Реализацию базового типа публикует билдер. Ничего не публикуется, пока не вызван `register()`:

```kotlin
val registration = scope.type<Action, MessageAction>()
    .name("message")
    .alias("msg")
    .priority(10)
    .access(ConfigTypeAccess.everyone())
    .register()
```

Без `access` тип виден только владельцу. Закрытие `registration` убирает тип.

## Секреты и отчёты поддержки

Поле с паролем, токеном или вебхуком помечается `@field:ConfigSecret`: его значение скрывается в отчёте `/pn debug`,
отдельной записи в `config.yml` не нужно. Остальное из настроек библиотеки: `privacy` (по умолчанию выключено) и
список `excluded-paths` с путями вида `плагин/файл.yml:ключ`.

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
