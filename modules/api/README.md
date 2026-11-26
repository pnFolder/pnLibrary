# pnLibrary API: карта пакетов

Всё, что видит плагин. Вход один: `PnLibraryProvider.get()`. Дальше по группам:

| Нужно | Где | Пакеты |
|---|---|---|
| Написать в чат, консоль, группе игроков | `library.audiences` | `audience`, `network` |
| Подписаться на событие | `library.events` | `events` |
| Логи, метрики, диагностика | `library.observe` | `logging`, `metrics`, `diagnostics`, `observability` |
| Зарегистрировать плагин, сервис, расширение | `library.registry` | `plugin`, `services`, `expansions`, `placeholders` |
| Посмотреть всё зарегистрированное одного вида (владелец, видимость) | `Registry<T>` | `registry` |
| Версия, платформа, прокси | `library.runtime` | `runtime`, `platform`, `version`, `compat` |
| Обновления плагина | `library.updates` | `updates`, `downloads`, `remote` |
| Конфиги | `library.configurations` | `config` |
| Команды | `plugins.register(this)` | `commands` |
| Текст, плейсхолдеры | `text`, `chat`, `placeholders` | |
| Сценарии, задачи, кулдауны | `scenario`, `tasks`, `cooldowns` | |

```kotlin
val library = PnLibraryProvider.get()
library.audiences.filterAudience { it.hasPermission("shop.admin") }.sendMessage(Component.text("Магазин перезагружен"))
library.observe.logging
library.registry.plugins.register(this)
```

Старые поля `PnLibrary` (`logging`, `plugins` и т.д.) работают как раньше. Пакеты из версии `0.0.1-dev.6` не переносятся.
