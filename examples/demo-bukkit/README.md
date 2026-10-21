# Демо-плагин (Bukkit)

Отдельный плагин, который показывает почти все возможности pnLibrary так, как их стоит использовать в настоящем
плагине. pnLibrary в его jar не встроена: на сервере должна стоять обычная pnLibrary, а демо подключается к ней
через `PnLibraryProvider`.

## Запуск

1. Соберите `gradlew :examples:demo-bukkit:build`.
2. Положите `examples/demo-bukkit/build/libs/pnLibrary-demo-bukkit-<версия>.jar` в `plugins/` рядом с
   `pnLibrary-<версия>-bukkit-java8.jar`. Берите собранный shadow-jar, а не `*-plain.jar`: в него уже включён
   модуль локализации со своим Gson в изолированных пакетах.
3. Запустите Paper или Spigot и выполните `/pndemo status`.

## Команды

| Команда | Что показывает |
|---|---|
| `/pndemo status` | контекст плагина; публикует тестовое событие и выполняет все стандартные действия |
| `/pndemo balance` | баланс игрока через API валюты |
| `/pndemo give <сумма>` | изменение валюты и кулдаун |
| `/pndemo reload` | перезагрузку конфига демо |
| `/pndemo-lib status` (`/pndemoapi status`) | то же через команды pnLibrary |
| `/pndemo-lib give <сумма>` | типизированный аргумент и асинхронный ответ |

`/pndemo-lib` регистрирует сама библиотека, в `plugin.yml` её нет.

## Что где лежит

| Файл | Что показывает |
|---|---|
| `ShopExamplePlugin.kt` | **полный пример команд**: свои ошибки, пять уровней ответа, проверка прав. Подробно в [docs/commands.md](../../docs/commands.md) |
| `DemoPlugin.kt` | жизненный цикл, регистрацию плагина и модулей, аккуратное выключение |
| `DemoDeclarations.kt` | объявление обновлений и прямых загрузок файлов |
| `DemoConfig.kt` | типизированный YAML-конфиг, аннотации и дописывание значений по умолчанию |
| `DemoCurrency.kt` | своя валюта: описание, баланс, пополнение, списание, установка, сброс, формат |
| `DemoPlaceholders.kt` | плейсхолдеры: кэш, запасное значение, публикацию в PlaceholderAPI |
| `DemoCommands.kt` | обычную команду Bukkit и команду pnLibrary с типизированным аргументом |
| `DemoLibraryEvents.kt`, `DemoBukkitEvents.kt` | события библиотеки и слушатель Bukkit |
| `DemoDiagnostics.kt` | живой снимок диагностики и файл конфигурации в отчёте |
| `DemoLocalization.kt` | ленивую загрузку переводов Minecraft с учётом версии |
| `DemoState.kt` | потокобезопасное состояние, общее для нескольких модулей |
| `DemoActionShowcase.kt` | все стандартные действия (`Action`), все условия и перечисления |

Демо использует только публичный API и модуль `minecraft-localization`. Классы `core` ему не нужны.

## Зависимости плагина

Зависимости объявляются одним DSL:

```kotlin
builder.depends(
    Dependencies.managed("pnlibrary", "1.0.0", "pnFolder", "pnLibrary"),
    Dependencies.plugin("Vault", "1.7.3", "https://example.org/vault"),
)
```

`managed` добавляет компонент pnFolder в общий граф обновлений. `plugin` описывает сторонний плагин. Для
стороннего плагина вместо страницы можно указать проверенный HTTPS-файл с размером и SHA-256:

```kotlin
Dependencies.plugin(
    "OptionalPlugin", "1.0.0", "https://example.org/plugin.jar",
    size = 1234,
    sha256 = "<64 шестнадцатеричных символа>",
    required = false,
    automaticDownload = true,
    forceAutomaticDownload = true,
)
```

| Параметр | Что значит |
|---|---|
| `required = false` | запуск не блокируется, если плагина нет |
| `automaticDownload = true` | загрузка разрешена с вашей стороны. Решает всё равно `downloads.yml` администратора: `enabled`, `automatic`, список разрешённых сайтов и папок |
| `forceAutomaticDownload = true` | обходит только `automatic: false`. Не обходит `enabled`, HTTPS, список сайтов, проверку размера и SHA-256 |

## Слушатели событий

Слушатель не входит в декларативный `PluginBuilder`. Он регистрируется после получения контекста через
`context.events.register(...)` или `context.events.subscribe(...)` и снимается сам при `context.close()`. Так
подписки лежат рядом с кодом жизненного цикла.
