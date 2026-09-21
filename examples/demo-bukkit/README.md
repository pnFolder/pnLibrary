# pnLibrary Demo Bukkit

Это отдельный production-style демонстрационный плагин. Он не встраивает pnLibrary в свой JAR:
на сервере должен быть установлен обычный `pnLibrary` runtime, а demo подключается к нему через
`PnLibraryProvider` и `PluginContext`.

## Запуск

1. Соберите `:examples:demo-bukkit:build`.
2. Положите `examples/demo-bukkit/build/libs/pnLibrary-demo-bukkit-*.jar` в `plugins/` рядом
   с соответствующим `pnLibrary-*-bukkit-java8.jar` и модулем `pnLibrary-minecraft-localization-*.jar`.
3. Запустите Paper/Spigot и выполните `/pndemo status`.

Команды:

- `/pndemo status` — показывает контекст и публикует тестовое событие;
- `/pndemo balance` — читает баланс игрока через Currency API;
- `/pndemo give <amount>` — демонстрирует мутацию валюты и cooldown;
- `/pndemo reload` — перезагружает конфигурацию demo.
- `/pndemo-lib status` (`/pndemoapi status`) — та же проверка через portable Command API pnLibrary;
- `/pndemo-lib give <amount>` — аргумент с типизированным parsing и асинхронным ответом.

При включении плагин регистрирует lifecycle summary, bStats-метрики, diagnostics snapshot,
typed placeholder `pndemo_coins`, повторяющуюся задачу, listener и lightweight currency.
Команда `pndemo-lib` регистрируется самой библиотекой и не требует ручной записи в `plugin.yml`.
Также демонстрация использует конфигурационный scope, typed services, component serialization,
Action API, Minecraft localization (ленивая загрузка `ru_ru`/`en_us`), update declaration и
отдельное direct-download declaration без автоматической загрузки.

## Карта исходников

Демонстрация намеренно разделена по ответственностям, чтобы её можно было использовать как
шаблон реального плагина:

| Файл | Что показывает |
|---|---|
| `DemoPlugin.kt` | жизненный цикл, регистрацию `PluginContext`, graceful shutdown |
| `DemoDeclarations.kt` | API-совместимые обновления и независимые direct downloads |
| `DemoConfig.kt` | типизированный YAML scope, аннотации и синхронизацию defaults |
| `DemoCurrency.kt` | descriptor, balance/deposit/withdraw/set/reset/format операции |
| `DemoPlaceholders.kt` | кэширование, fallback и публикацию в PlaceholderAPI |
| `DemoCommands.kt` | native Bukkit-команда и portable command builder с typed argument |
| `DemoLibraryEvents.kt` / `DemoBukkitEvents.kt` | библиотечные события и platform listener |
| `DemoDiagnostics.kt` | динамический diagnostics snapshot и конфигурационный файл |
| `DemoLocalization.kt` | ленивый version-aware cache локализаций Minecraft |
| `DemoState.kt` | потокобезопасное состояние, используемое несколькими модулями |
| `DemoActionShowcase.kt` | все стандартные `Action`, все `ActionCondition`, `Expansion`-модели и enum-ветки |
| `DemoUpdateFeature.kt` | optional update feature: freeze store, manifest codec и bounded artifact downloader |

`modules:api`, `modules:common`, `modules:features:update` и `modules:features:minecraft-localization` используются напрямую.
`modules:core`, `runtime-spi` и platform runtime — внутренние реализации pnLibrary: их нельзя
подключать к прикладному плагину вместо обычного runtime, поэтому они демонстрируются самим
runtime и не дублируются в example-плагине.

При `/pndemo status` showcase выполняет полный граф действий: message, action-bar, sound,
particle, effect, console log, no-op, sequence, delay, switch, conditional и операции со
значениями. Отдельно создаются `UpdatePlaceholderAction`, все условия доступа/вероятности/
сравнения и полный набор `Expansion` expression/operator/value моделей. Это сделано намеренно:
demo является API-каталогом, а не только красивым примером одного сценария.
