# Конфигурация

Конфигурация описывается обычным классом с значениями по умолчанию. Библиотека сама создаёт YAML-файл,
дописывает недостающие поля, сохраняет комментарии и проверяет значения.

## Модули

| Модуль | Назначение |
| --- | --- |
| `pnlibrary-config-api` (`:modules:features:config-api`) | Типы, аннотации и `ConfigScope`. От него зависят плагины. |
| `pnlibrary-config` (`:modules:features:config-runtime`) | Реализация на SnakeYAML. Входит в сборку pnLibrary. |

Пакет API: `ru.privatenull.pnlibrary.api.config`.

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

Дальше: `config.value` — текущее значение, `config.reload()` — перечитать файл, `config.save()` — записать.

Из Java используйте `scope.yaml(path, Settings.class, Settings::new)`.

## Проверка значений

Работают два способа, их проблемы складываются в один `ConfigValidationException`:

1. **Аннотации** на полях: `@ConfigRange`, `@ConfigNotBlank`, `@ConfigPattern`, `@ConfigRequired`.
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
| `migrations` | Версионная цепочка миграций `ConfigMigrationPlan`. |
| `naming` | Преобразование имён полей в ключи YAML, например `KEBAB_CASE`. |

Явное имя ключа задаёт `@ConfigKey` и всегда сильнее `naming`.

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
