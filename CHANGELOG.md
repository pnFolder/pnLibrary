# Изменения

## Порядок в структуре (ломающие изменения)

**Bukkit API: только контракты**
- `ItemFactory`, `HeadUtil` и `ItemStackCodec` заменены сервисом `BukkitPlatform.items` (`ItemService`):
  `fromSection`, `fromMap`, `write`, `writeExact`, `toMap`, `head`, `encode`, `decode`, `isReal`.
- Предмет можно собрать вручную: `items.create(itemSpec { material(...); name(...); customize { ... } })`.
- Свой формат конфига (`ItemFormat`): другие имена ключей и свои поля (`field("glow") { item, value -> ... }`).
  `items.read(...)` отдаёт `ItemSpec`, который можно поправить через `toBuilder()` перед созданием.
- `VisualEntity` стал интерфейсом. Создание через `BukkitPlatform.visuals` (`VisualService`): `item`, `block`, `text`.
  `entity()` стал свойством `entity`, `isDead` удалён (это `!isValid`).
- Реализации переехали в `platforms/bukkit/runtime`. Сборка запрещает рефлексию в публичных модулях.
- `MenuApi.kt` переименован в `Menu.kt` (типы не менялись).

**Имена**
- `api.plugin.RemotePolicy` → `RemotePolicySettings`: настройки «откуда брать политику». `api.remote.RemotePolicy`
  остался самой политикой.
- Удалён второй `ObservabilityService` из `api.diagnostics`. `PnLibrary.observability` теперь
  `api.observability.ObservabilityService`. `observability`, `activity` и `audiences` больше не бросают
  `UnsupportedOperationException` по умолчанию.
- Внутренний `core.runtime.PnLibraryBootstrap` → `PnLibraryLauncher`, чтобы не путать с установщиком.

**Модули**
- `modules/design/console` → `modules/console`, артефакт `pnlibrary-console`.
- `modules/bootstrap/bukkit` → `platforms/bukkit/bootstrap`, пакет `org.pnfolder.pnlibrary.bukkit.bootstrap`.

**Встраиваемый установщик на всех платформах**
- Общая часть в `modules/bootstrap` (`pnlibrary-bootstrap`), входы `pnlibrary-bootstrap-bukkit`, `-bungee`, `-velocity`
  с одинаковым `PnLibraryBootstrap.ensureInstalled(..., "версия")` и `ensureCompatible(...)`.
- Скачивается только `universal.jar`. Релиз теперь публикует и его.
- На прокси скачанная pnLibrary подключается после перезапуска. Устаревшая версия получает понятную карточку
  с командой обновления.
- `BootstrapVersion` стал публичным.
- `modules/internal/faststats-velocity` → `platforms/velocity/faststats`, пакет `org.pnfolder.pnlibrary.velocity.faststats`.

**Удалено неиспользуемое**
- Модели `actions.standard.check.Expansion` и `ProductReleaseManifest`: их никто не исполнял и не читал.
- Синонимы `PluginBuilder.dependency`, `apiVersion`, `apiVersions`, `artifactPattern`.

**Документация**
- `docs/updates.md`, `docs/currency.md`, `docs/architecture.md` и README модулей и примеров переписаны на русском
  как руководства.

## 0.0.1-dev.1 — большая чистка

**События (ломающие изменения)**
- `Cancellable` удалён. Новый `ResultedEvent<R>`: событие несёт типизированный результат
  (`GenericResult`, `ComponentResult` с причиной в виде `Component`). У любого события есть `isAllowed`.
- Подписка через билдер: `scope.on<Event>().priority(...).ignoreCancelled().listen { ... }`.

**Конфигурация (ломающие изменения)**
- Конфигурация вынесена в модули `pnlibrary-config-api` и `pnlibrary-config`. Описание: [docs/configuration.md](docs/configuration.md).
- В `ConfigScope.yaml(...)` можно передать программный валидатор; он работает вместе с аннотациями.
- Полиморфные типы публикуются через билдер `scope.type<Base, Impl>()...register()`.
- Удалены `ConfigGroup` и `@ConfigNaming`.
- Изменения формата файла описываются шагами `ConfigSteps`: `version(n) { rename / put / remove / get }`.
  В каждый файл пишется маркер `_config-version` с предупреждением на двух языках.

**Безопасность**
- YAML-конфигурации читаются через `SafeConstructor`, раньше две точки использовали обычный конструктор.
- Публикация JAR при обновлении атомарна; при сбое установки pnLibrary прежний JAR возвращается.
- Транзакции валюты повторяются при конфликте сериализации (`40001`, `40P01`).

**Обновления**
- Новый каталог в стиле Catalog: `/pn updates` — список, карточка плагина, настройки, история, резервы.
- Каналы, автозагрузка и пауза (до 7 дней) — кнопками; после сохранения — заголовок на экране и звук.
- История по дням с автором каждого действия; пустые проверки скрыты.

**Сообщения**
- Один стиль для всего: каталог, `/pn status`, `/pncurrency`, `/pndebug`, консоль, заголовки — на русском.
- Цвета подобраны так, что и на 1.8 каждый остаётся своим.

**Диагностика и метрики**
- «Чёрный ящик» ловит ошибки на Bukkit, Bungee и Velocity, история хранится по сроку.
- bStats и FastStats за одним интерфейсом. Исправлено: `enabled: false` в bStats теперь действительно отключает отправку.

**Код**
- Большие файлы разбиты, обновления разложены по пакетам, копипаст между платформами убран.
- Общие помощники вместо копий: запись файлов, sha256, русские склонения и сроки.
- Безопаснее: без опасных `!!`, ошибки не теряются, внутренние классы закрыты.
- Новые тесты, включая архитектурные; сборка проверяет весь публичный API.
- Документация: [README](README.md), [docs/](docs/).
