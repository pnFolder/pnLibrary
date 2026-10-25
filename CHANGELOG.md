# Изменения

## Внутренние контракты и фабрики ресурсов

- Реестр плагинов и фабрика ресурсов модулей зависят от контрактов конфигураций, валюты, плейсхолдеров,
  сервисов и загрузок. Конкретные реализации выбираются при сборке рантайма. Убран неявно создаваемый
  конфигурационный сервис из конструктора реестра плагинов.
- Ресурсы модуля хранят закрываемый `ServiceScope`, не вложенный класс реализации реестра сервисов.
  При ошибке создания модуля scope закрывается целиком вместе со всеми уже полученными ресурсами.
- Общие расширения используются через существующий `ExpansionManager`, а карточки состояния
  плейсхолдеров — через `PlaceholderAdapterRegistry`. Публичные сигнатуры API плагинов сохранены.

## Надёжность валюты, загрузок и кеша локализации

- JSON- и JDBC-хранилища валюты проверяют запросы теми же правилами, что и провайдер: отрицательные суммы,
  лишние десятичные знаки, отсутствующие счета и переводы на тот же счёт отклоняются без изменения баланса.
  Отклонения сохраняются в истории и учитывают ключ повторного запроса. `CurrencyDescriptor.accepts()`
  возвращает `false`, если требуется округление при `RoundingMode.UNNECESSARY`.
- Повреждённый кеш списка версий Minecraft изолируется и загружается заново. Ответ Mojang проверяется до
  сохранения: некорректный список не заменяет рабочую копию и сообщает ошибку `MALFORMED_DATA`.
- Отказ проверки скачанного JAR удаляет временный файл. Публикация загрузки отклоняет каталог или символическую
  ссылку по пути назначения. При сбое отката восстанавливаются остальные файлы, резервные копии сохраняются,
  а ошибки восстановления прикладываются к первоначальной ошибке.

## Типизированные результаты и единый стиль API (ломающие изменения)

- `playSound`, `showTitle`, `applyEffect`, `spawnParticle` и `runCommand` возвращают `Delivery` вместо `Boolean`:
  `Delivered`, `Unsupported` или `Failed(причина)`. Проверка успеха — `delivery.isDelivered`. Нужно поправить свои реализации
  `LibraryPlayer` и `LibraryAudience`.
- У билдеров `PlaceholderAccess`, `RemotePolicyContext`, `ConfigOptions`, `ConfigTypeAccess`, `CurrencyAccess` и
  `CurrencyDescriptor` конструктор стал `internal`: создавайте их через `builder()`. Тест архитектуры следит, чтобы публичных
  конструкторов у билдеров больше не появлялось.
- Публичные настройки времени принимают `Duration` вместо чисел (например, `confirmationTimeout` у валюты).
- `ResultedEvent.callResult()` вызывает событие и сразу возвращает его типизированный результат.
- Появился `ComponentTemplate.renderAsync()`; шаблоны плейсхолдеров больше не блокируют поток при условиях и локальных выражениях.
  `render()` остался и ждёт результат.
- Запасные пути не ловили `NoClassDefFoundError` и `NoSuchMethodError` (несовместимый мост платформы), потому что любой `Error`
  считался фатальным. Теперь фатальна только `VirtualMachineError` (`Failures.rethrowIfFatal`), как в обработке событий.

## Локализация Minecraft и расширения

- Официальные переводы Mojang вынесены в нейтральный модуль `minecraft-localization-api`, реализация для Bukkit — отдельно.
  Локализация есть только на игровых серверах и доступна через `ModuleContext.localization`. См. `docs/localization.md`.
- Перевод любых объектов Bukkit (`Material`, `EntityType`, `Enchantment` и других) возвращает `Translation`; языки заданы типом
  `MinecraftLocale`.
- Поиск по названию с опечатками и неверной раскладкой клавиатуры: `findExact`, `search`, `fuzzy`, `suggest`, `best`. Раскладки
  расширяемы через `KeyboardLayouts`.
- Система расширений (`Expansion`, `ModuleContext.expansions`, `PnLibrary.expansions`) снова основная. Собственные службы библиотеки
  вынесены в отдельный реестр, плагин не может их отключить. См. `docs/expansions.md`.

## Удалённые политики только на Java (ломающее изменение)

- Поддержка `.kt`-политик убрана вместе с отдельным файлом `pnLibrary-policy-kotlin` (около 62 МБ) и параметром
  `-Dpnlibrary.policy.kotlinCompiler`. Удалённая политика — один `.java`-файл, его компилирует JDK сервера.
  Ссылка на `.kt` теперь отклоняется с понятной ошибкой, дальше действует `onError`. См. `docs/remote-policies.md`.

## Единый список файлов релиза (ломающее изменение `.pnlibrary/releases.json`)

- Блок `installer` убран. Файл и сумма для встроенного установщика теперь берутся из той же записи `artifacts`,
  которую читает обновление уже установленной pnLibrary — одна структура вместо двух. Запись получает
  `"platform": "UNIVERSAL"` для файла, подходящего всем платформам.
- `artifacts` может перечислять сразу несколько файлов одной платформы под разные версии Java (например, отдельно под
  Java 8 и под Java 21); установщик и обновление выбирают файл по платформе и версии Java, предпочитая точное
  совпадение платформы записи `UNIVERSAL`.
- У записей `artifacts` появились необязательные `sha256` и `size`; раньше это было только у `installer`. Поле `java`
  тоже стало необязательным (без него файл не ограничен по Java — для этого и нужен был `UNIVERSAL`).
- Обновления через `.pnlibrary/releases.json` (не только у самой pnLibrary, у любого продукта) теперь реально
  проверяют SHA-256 скачанного файла — раньше сумма из каталога никуда не доходила, и обновление с `requireSha256`
  (включено по умолчанию) просто не могло состояться. См. `docs/repository.md`.

## Карточки включения и выключения

- Плагин может скрыть строки, которые pnLibrary добавляет сама: `lifecycle.enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.PLATFORM)`.
  `LifecycleRow.NOT_CONFIGURED` убирает все строки «SKIP: not configured». Строки самого плагина не скрываются.
- Консольные карточки сами выбирают `┌ ├ └` по месту строки в группе; подписи белые, значения в цвете статуса.

## Плейсхолдеры в действиях и проверках (ломающие изменения)

- Три вида, у каждого свои скобки: `[имя]` — значение действия, `{имя}` / `{плагин:ключ}` — плейсхолдеры pnLibrary,
  `%имя%` — только PlaceholderAPI. Запись `[[имя]]` больше не читается.
- `%имя%` раньше искалось по всем источникам подряд, и в тексте сообщений не раскрывалось вовсе; теперь это явный путь к
  PlaceholderAPI. Свои значения и плейсхолдеры pnLibrary пишутся в `[ ]` и `{ }`.
- Формат одного плейсхолдера: `PlaceholderBuilder.format(name) { value, arguments, request -> ... }`, значение
  приходит нужного типа, формат виден только этому плейсхолдеру.
- Новые форматы: `number:N`, `group`, `short`, `capitalize`, `trim`. Свои форматы регистрируются через
  `PlaceholderService.formatter`.

## Репозиторий и установщик

- Правила папки `.pnlibrary` описаны в [docs/repository.md](docs/repository.md): `releases.json` и `changelogs/<версия>.md`.
  Тест репозитория проверяет, что списки читаются и у каждой версии есть changelog.
- Встроенный установщик выбирает версию так: сначала список `.pnlibrary/releases.json` (самый новый стабильный, а если
  стабильных там нет, самый новый из имеющихся: beta, alpha, dev, release candidate); если список пуст или версии нет на
  GitHub, то релизы GitHub (стабильный, иначе любой); если нет ничего, в консоли карточка «недоступна». Файл и контрольную
  сумму он берёт из блока `installer` списка, а если блока нет, из GitHub-релиза выбранной версии. Когда ставится
  нестабильная версия, консоль говорит об этом.
- Минимальную версию в установщике можно не указывать: `ensureInstalled(plugin)` и `BootstrapOptions.builder()`. Тогда ставится
  самый новый стабильный релиз, а если стабильных нет, самый новый релиз любого рода. Без выпущенных релизов
  в консоли показывается карточка «недоступна».
- Репозиторий, имя и id продукта установщик и самообновление берут из `gradle.properties`, а не из строк в коде.

## Метрики

- Отправка bStats одна на все платформы (`BStatsClient`), раньше было три копии `Metrics.java`. На платформе остаётся
  только своё: название, поля о сервере, планировщик, лог. Файл настроек `plugins/bStats/config.yml` (Bukkit,
  BungeeCord) и `config.txt` (Velocity) общий для всех плагинов сервера, как и раньше.
- BungeeCord больше не отключает метрики, если не удалось прочитать файл настроек: идентификатор берётся в памяти, как на
  Bukkit. Файл, который нельзя прочитать, считается отказом: данные не отправляются.
- Velocity больше не получает `Metrics.Factory` через Guice.

## Команды: всё под `/pn` (ломающие изменения)

- `/pnevents` → `/pn events`, `/pndebug` → `/pn debug`, `/pncurrency` → `/pn money` (только Bukkit). Отдельной
  остаётся только `/pninfo`.
- На BungeeCord и Velocity тоже есть `/pn`: `/pn events`, `/pn debug` и `/pn update …` (бывший `/pnupdate`, только
  консоль). Команд `/pnevents`, `/pndebug`, `/pnupdate` на прокси больше нет.
- У `/pn` есть запасные названия `/pnlib` и `/pnlibrary`: если другой плагин занял `/pn`, управление остаётся
  доступным (ещё работает `/pnlibrary:pn`, которое Bukkit добавляет сам).
- У `/pn` больше нет права на корне: каждая административная подкоманда просит `pnlibrary.admin` сама, а
  `/pn events` и `/pn debug` — свои `pnlibrary.events.view` и `pnlibrary.debug`. Модератору с одним правом на журнал
  не нужен `pnlibrary.admin`.
- Подтверждения сведены в одну команду. `update-confirm`, `update-status`, `update-rollback` и `restart` убраны:
  `/pn update rollback` и `/pn update restart` выдают одноразовый код, `/pn update confirm <код>` выполняет то, для
  чего код выдан. Откат к резервному набору (`/pn updates rollback <id> [configs]`) подтверждается так же, слово
  `confirm` в конце команды больше не нужно. `update-confirm` была мёртвой: код для неё нигде не выдавался.
- Статус плана: `/pn update status [плагин]`. Слова `confirm`, `status`, `rollback`, `restart` заняты командой, плагин с
  таким именем не может зарегистрировать обновления (ошибка при регистрации).
- API: `CommandBuilder.mount(name, definition)` встраивает готовую команду как подкоманду; её обработчики видят
  свои аргументы без ведущего слова.

## Действия и проверки (ломающие изменения)

**Действия — только объекты с `type`**
- Строки `[message] текст`, `<delay=…>`, `<chance=…>`, `[wait 2s]` больше не читаются. Ошибка показывает,
  как записать то же действие через `type`.
- Обёртки: `delay`, `chance`, `if`, `require`, `repeat`, `group` с действиями в `do:`. `delay` без `do`
  ставит на паузу всё, что ниже.
- `type: console` теперь команда от консоли (как в DeluxeMenus), запись в журнал — `type: log`.
  `type: player` — команда от игрока.
- Удалены `SwitchAction`, `NoOpAction`, `SetValueAction`, `CopyValueAction`, `RemoveValueAction`,
  `UpdatePlaceholderAction`, `WaitAction` (это `DelayAction` без `do`), `ConditionalAction` (это `IfAction`),
  `RequirementsAction` (это `RequireAction`). `SequenceAction` → `GroupAction`, `ConsoleLogAction` → `LogAction`.
- Встроенные действия переехали в `api.actions.builtin`.

**Проверки — отдельный пакет `api.checks`**
- `ActionCondition` → `Check`, условия `*Condition` → `*Check` (`api.checks.builtin`).
- Новые сочетания: `all`, `any`, `not`, `at-least`; проверка по имени (`check: has-100`).
- `Requirement` — проверка с `deny` и `success`; плагин передаёт свой блок `checks:` в
  `ActionService.context(..., checks = ...)`. `check(context)` — только ответ, `test(context)` — ответ и действия.

**Пакеты**
- `LibraryPlayer`, `LibraryAudience`, `PlayerEffect`, `PlayerParticle`, `ItemQuery` → `api.audiences`.
- `DependencyBuilder`, `PluginDependency`, `DownloadPolicy`, `VersionConstraint`, `ExternalPluginDependency`,
  `ExternalArtifact`, `ManagedProductDependency` → `api.dependencies`.
- `MetricsBuilder`, `MetricsController` → `api.metrics`; `RemotePolicySettings`, `DenyAction`,
  `PolicyErrorAction` → `api.remote`.
- `api.updates`: релизы и план → `api.updates.release`, откаты и история → `api.updates.backup`.

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
