# Изменения

## Ветка next (0.1.0-dev)

- Автозагрузка недостающих плагинов и файлов включена всегда, выключить её нельзя: убраны настройка `downloads.automatic`, команда `/pn deps auto`, переключатель в меню и совет «включите автозагрузку» в консоли. `DownloadPolicy.FORCED` теперь то же, что `AUTOMATIC`. Старое значение в `downloads.yml` и `preferences.state` пропускается, файл выбора удаляется. Из справки убраны записи о командах, которых нет: `/pn updates set … auto`, `/pn servers … auto`.
- Версия 0.1.0-dev.6: API несовместим с dev.5, поколение API (`PnLibraryApi.VERSION`) поднято с 1 до 2. Плагины, которые объявляли `pnLibraryApi(1, 1)`, нужно перевести на новое API и объявить `pnLibraryApi(2, 2)`.
- Плейсхолдеры: владелец и видимость. Плейсхолдеры, форматы и конвертеры принадлежат модулю и снимаются с ним; `Visibility` (`MODULE`, `PLUGIN`, `ALL`) задаётся на `@Placeholder`/`@Placeholders` и решает, кому плейсхолдер виден (форматы и конвертеры всегда общие), побеждает ближайший, одинаковые адреса дают предупреждение. В адресе и в формате можно писать владельца: `{shop/main@shop.price|~~~@upper}` (`~` свой плагин, `~~` свой модуль, `~~~` библиотека). Плейсхолдер может быть внутри плейсхолдера: `{clan.of.{player.name}.level}`, до 16 уровней. `PlaceholderService.converter(type, converter)` заменяет `ArgumentAdapters` для модуля. Описание: `docs/placeholders.md`.
- Действия, требования и функции выражений: уровень `DefinitionScope.PLUGIN`, побеждает ближайший владелец (раньше имя было одно на всех и конфликт был ошибкой), владельца можно написать перед именем: `~@give-kit`, `~~@give-kit`, `shop/main@give-kit`. Определения модуля снимаются при его закрытии.
- Убран `PluginAccess` и всё, что на нём стояло: `currency.access(...)`, `CurrencyPlaceholderOptions.access`, `CurrencyProviderRegistry.register(..., access)` и `ConfigScope.extensions(...)`. Валюты и типы конфигураций доступны всем плагинам; в плагинах нужно удалить эти вызовы. Для плейсхолдеров, форматов и действий остаётся `Visibility` (`MODULE`, `PLUGIN`, `ALL`).
- Команды и меню: `/pn placeholders info <адрес>` и новая команда `/pn actions` (право `pnlibrary.actions`, раздел «Действия» в `/pn gui`) показывают владельца, видимость и все формы записи с `~`, `~~`, `~~~`, `плагин/модуль@`. Скрытый чужой плейсхолдер пишет в лог, почему он не виден.
- Аудитории (пакет `api.audience`, как в Adventure и `spi.audience`; ломает API: `CommandSender` теперь `Audience`):
  - Вместо `LibraryAudience` и `AudienceSender` обычный Adventure `Audience`. Имя, `displayName`, `locale`, права и вид отправителя (`isPlayer`, `isConsole`, `id`) читаются расширениями из указателей Adventure. Платформы строят указатели сами (`senderPointers`, для игроков `playerPointers`), поэтому проверка прав работает на любой версии Minecraft, а не только там, где сервер сам отдаёт `PermissionChecker`.
  - `Audiences` (`PnLibrary.audiences`): `console()`, `server()`, `players()`, `player(uuid | name)`, `filter { }`, `withPermission()`, `of(native)`. Рассылка по группе не прерывается из-за одного получателя, это работает для всех методов Adventure (заголовки, боссбар, звук, книга).
  - `OnlinePlayer` только `uniqueId` и указатели. Остальное методами с умолчанием «не умею» (`addEffect`, `spawnParticle`, `runCommand`, `countItems`, `experienceLevel`, `position`), как у Adventure; Bukkit переопределяет, прокси нет. Значения без игрока в `api.world`: `StatusEffect`, `Particle`, `Position`, `ItemMatcher`.
  - `Delivery` переписан в один класс-отчёт: `status` (`DELIVERED`, `PARTIAL`, `UNSUPPORTED`, `FAILED`), `receipts` (ответ каждого получателя: имя, исход, причина сбоя), `delivered`, `failures`, `failure`, `orThrow()`. Фабрики `Delivery.delivered()`, `unsupported()`, `failed(cause)`, `of(accepted)`, `combine(...)`, `to(audience, operation)`. Вместо прежних `Delivered`/`Unsupported`/`Failed`. `Delivery.to(audience, it -> it.sendMessage(...))` работает для любой аудитории Adventure: одного получателя или группы (каждый получает операцию, даже если другой упал; пустая группа это `UNSUPPORTED`; частичный успех теперь `PARTIAL`, а не потерян).
  - Звук и заголовок больше не возвращают `Delivery`; он остался у эффектов, частиц и команд. `LibraryAudience.dynamic` заменён функцией `dynamicAudience`.
- Чат: подпапка `api.chat.style` убрана. `ChatPalette` лежит в `api.chat`, `ChatTone`, `ChatNotice` и `ConfirmChoice` собраны в один файл `ChatMessages.kt`. Меняется только путь импорта: `api.chat.style.ChatPalette` стал `api.chat.ChatPalette`, то же для `ChatTone` и `color`. В `ChatKit` кнопки команд (`button`, `option`) используют один общий код.
### Сценарии в одной папке
- Отчёт `/pn debug`: папки модулей читаются как `plugins/pnclans/modules/war/` вместо `m-pnclans-war-<хеш>`. `/pn debug pnClans` теперь включает и модули плагина (раньше их записи не попадали в отчёт по имени плагина).
- Отчёт `/pn debug`: логи, журнал наблюдений и конфигурации входят всегда. Флаги состава убраны совсем (`--full`, `--logs`, `--config`), остался только `--local`; со старыми флагами команда ответит «Неизвестные аргументы». В `manifest.json` вместо `diagnosticsMode` поля `logs` и `configs`.
- Плейсхолдеры: у `PlaceholderBuilder` убраны `publishToPlaceholderApi()` и `publishToPlaceholderApi(namespace)`. Остались `publish(PlaceholderPublication)` и `publishToPlaceholderApi(namespace, name)`: имя в PlaceholderAPI всегда задаётся явно.
- Убрано из билдеров: `RemotePolicySettings.support(url)` (ссылка помощи всегда одна, из библиотеки) и `PluginUpdateRequest.Builder.channel(...)` / `automaticDownload(...)`. Канал и автозагрузку выбирает администратор командами `/pn updates set` и в меню; по умолчанию стабильный канал и автозагрузка плагинов включена, у самой библиотеки выключена. В плагинах нужно удалить эти вызовы.
- Загрузки: `maxBytes` задаёт наибольший размер для одного файла, больше или меньше общего потолка в 512 МБ (`FileDownload`, `variant`, зависимости).
- Загрузки: без SHA-256 файл берётся с первой ссылки, которая ответила; сравнение копий между зеркалами и запоминание сумм убраны, остальные адреса служат запасными.
- Зависимости плагинов, варианты файла: `variant(...)` задаёт отдельный файл под платформу и версию Minecraft со своими ссылкой, SHA-256 и зеркалами; скачивается и проверяется только подходящий. В меню появился экран «Что не хватает» со скачиванием группы кликом.
- Зависимости плагинов: у `ExternalPluginDependency` появились `title` и `description`. Карточка «не хватает плагинов» и `/pn deps` делят список на обязательные и необязательные, показывают название, описание, все ссылки и способ проверки файла (есть ли SHA-256 автора). Описание в `docs/dependencies.md`.
- Обновления, ремастер API: `UpdateService` разделён на пять частей: `products`, `plans`, `settings`, `backups`, `history`. Убраны методы-заглушки «не поддерживается»: в каждой части работает всё. `checkNow` стал `plans.check`, `currentPlan` стал `plans.current`, `history()` стал `plans.recent`, `setChannel` стал `settings.channel`, `setHeld` разделён на `settings.hold` и `settings.release`, `backups()` стал `backups.list`, `rollback(id, restoreConfigurations, actor)` стал `backups.rollback(id, actor, restoreConfigurations)`, `historyPage` и `historyCount` стали `history.page` и `history.count`. `confirm` из API убран: он никогда не работал, подтверждение идёт командой `/pn update confirm`. Все методы с `actor` его принимают необязательным.
- Плейсхолдеры в тексте: один вид скобок `{...}` вместо трёх. `{имя}` читает сначала значение запуска (`player`, `price`), затем плейсхолдер pnLibrary; `{плагин/модуль:путь}` адресует плейсхолдер явно, `{papi:имя}` берёт из PlaceholderAPI (`%имя%` остаётся тоже). Старые `[имя]` больше не плейсхолдер: префикс вроде `[pnTest]` остаётся текстом и не даёт предупреждения «Placeholder … is not registered». Нужно заменить `[имя]` на `{имя}` в действиях, сообщениях и выражениях. Описание в `docs/placeholders.md`.
- Меню «Библиотеки плагинов»: сначала выбор плагина (сколько у него библиотек, модулей, какой размер, есть ли конфликт версий) или «Все библиотеки» одним списком; у библиотеки «Принадлежит» (плагину или модулю плагина), «Файл» и «Папка» считаются от папки сервера, а не обрезанный хвост пути.
- Диск: `/pn modules disk` и кнопка «Диск плагинов» в меню показывают, сколько места занимает каждый плагин сервера (jar, настройки, библиотеки, журналы, данные), сколько свободно на диске. У библиотек плагинов видны размер и место, где лежит jar.
- Библиотеки: на плагин (`PluginRegistration.libraries`) и на модуль, `title` и `description` у библиотеки и у модуля (`metadata { description(...) }`), `loadClass`, `find`, `remove`, `PluginRegistry.allModules()` и `libraryUsage()`, `/pn modules libs|plugins`, экраны «Библиотеки». `shared()` на Bukkit и BungeeCord грузит в загрузчик плагина-владельца.
- Версии Minecraft плагина и модуля: `supportedMinecraft(от, до)`, `supportedMinecraft(версия)`, `supportedMinecraftOnly(...)`, `supportedMinecraftFrom`, `supportedMinecraftUpTo` (константой `MinecraftVersion` или текстом) на `registration` и в `registerModule`, `onNewerMinecraft(NewerAction)`, `onUnsupportedMinecraft(UnsupportedAction)`, результат `ModuleContext.minecraft` (`MinecraftSupport`), `/pn modules mc force`. Версии сравниваются числами, поэтому новый релиз не ломает «от версии».
- Управление библиотекой, как у модуля: состояние `LibraryState` (загружена, выключена, ошибка, пропущена), `ModuleLibraries.statuses/status/setAutoLoad/retry`, выбор администратора сохраняется между перезапусками, команда `/pn modules lib enable|disable|retry`, состояния в `/pn modules libs`. Общие настройки `setup.libraryDefaults {}` и условие `setup.library(lib) { … }`.
- Библиотеки в регистрации: `setup.library(...)` и `setup.libraries(...)` в билдере `registerModule` (по координатам, готовой `ModuleLibrary` или с настройкой), без блока `libraries:` в `plugin.yml`.
- Библиотеки модуля: `PluginModule.libraries()` и `ModuleLibrary`. Откуда качать: один список мест `source { link(...) | repository(...) }`, у каждого своя сумма и имя, сколько угодно; пробуются по порядку, затем Maven Central, первое рабочее побеждает. `LibraryScope` (плагин, отдельный загрузчик, pnLibrary), `platforms`, необязательность, классификатор.
- Модули: `registerModule(id, PluginModule)` с методами `onEnable`/`onDisable`/`onReload`, `ModuleContext.control` (`enable`, `disable`, `restart`, `reload`, `autoStart`), команда `/pn modules` и экраны меню «Модули плагинов» (также кнопка «Модули» в карточке плагина). Выключенные навсегда модули хранятся в `data/modules/preferences.json`. Подробности в `docs/modules.md`.
- Обновления: в карточке продукта можно выбрать сборку Java (`UpdateService.setJavaBuild`, `UpdateSnapshot.javaBuilds/javaBuild/recommendedJava`, команда `/pn updates set <плагин> java <n|default>`). Без выбора берётся сборка, как у установленного продукта.
- `api.tasks`: по одному типу на файл и подпапки `spec` (`TaskSpec`, `TaskSpecBuilder`, `TaskAction`, `TaskContext`, `TaskExecution`, `TaskConflictPolicy`) и `info` (`TaskId`, `TaskStatus`, `TaskSnapshot`, `TaskQuery`). Новое: задача классом `ScheduledTask` (`scope.schedule(MyTask(args))`), `TaskStatus.isTerminal`, `TaskSnapshot.isActive`, `TaskQuery.matches(snapshot)`; дубли в `core` удалены. Импорты нужно поправить.
- (теперь в пакете `api.tasks.run` вместе с `TaskLagHandler`/`LagResponse`) `TaskContext` расширен: `key`, `tags`, `executionKind`, `interval`, `skippedCount`, `previousStartedAt`, `isCancelled`, `snapshot()`, `isFirstRun`, `isRepeating`, `lag`, `elapsed`, `every(n)`, `cancelAfter(n)`. Исправлено: `scheduledAt` у повторяющейся задачи теперь плановое время каждого запуска, а не время первого. Подзадачи: `context.later(delay) { ... }`, `context.spawn(spec или ScheduledTask)` запускают следующую задачу в той же области, без доступа к `tasks`; новые `execution` и `TaskContext.SUBTASK_TAG`. Новое в задачах: `maxLag` (пропустить запуск, который запоздал дольше заданного), `tolerateFailures` (повторяющаяся задача переживает N ошибок подряд), `TaskSnapshot.failedCount`, `TaskContext.isLate(порог)`.
- `PluginUpdateRequest`: правило `.artifact(...)` стало необязательным. Какой файл подходит серверу, решает каталог `releases.json` (платформа и диапазон Java у каждого файла), а Java плагина берётся из его `ProductDescriptor`.
- `api.updates` разложен: `request` (`PluginUpdateRequest`, `PluginUpdateArtifact`, `CatalogSource`), `product` (`ProductDescriptor`, `ProductId`, `UpdateChannel`), `history` (`UpdateHistoryEntry`); в корне остались `UpdateService` и `UpdateRegistration`.
- `api.proxy` переименован в `api.network` (`NetworkProxy`, `NetworkServer`, `NetworkPlayer`); `placeholders.model`, `placeholders.tree.scope`; файлы API, где лежало несколько разных классов, разделены по одному на файл (каталоги однотипных классов вроде ошибок команд и встроенных действий оставлены вместе).
- `metrics` и `observability` разложены: `metrics.provider`, `metrics.error`, `observability.report`, `observability.observation`; в ядре `metrics.session`, `metrics.factory`, `observability.store`.
- Остальные пакеты API разложены: `dependencies.external`, `diagnostics.report`, `plugin.lifecycle`, `plugin.metadata`, `audiences.player`; файлы с несколькими классами (`logging`, `dependencies`, `downloads`, `observability`, `metrics`, `cooldowns`) разделены по одному классу на файл. В ядре `diagnostics` разделён на `config`, `command`, `redaction`, `journal` на `store` и `view`.
- `LagResponse.CANCEL`: обработчик лага может отменить задачу насовсем, если после такой задержки запускать её уже незачем.
- Команды в API разложены: `api.commands.definition` (`CommandDefinition`, `CommandNode`, `ArgumentType`, DSL `command`) и `api.commands.error` (`CommandError`, `fail`, `ensure`); `CommandContext`, `CommandSender`, `CommandRegistration` остались в корне. Движок команд ядра лежит в `core.commands.engine`.
- Подзадачи — полноценные задачи: у них есть `parentId`, `context.repeat(n, every) { }`, `context.spawn { ... }` (DSL), `cancelWithParent` (отмена вместе с родителем); `/pn tasks` и меню показывают в `/pn tasks` и меню подзадачи вложены в родителя (список показывает только верхний уровень, у родителя кнопка «Подзадачи», у подзадачи «Подзадача от»), есть слот «Опоздания». Лаги сервера: `TaskLag` (`lag`, `missedRuns`), `ScheduledTask.onLag`, `onLag(порог, handler)`/`whenLate(порог, consumer)` в билдере, ответ `LagResponse` (RUN/SKIP), `TaskSnapshot.lastLag/worstLag`, «Опоздание» в чате и GUI.
- `api.text` разложен на `cache` (`ComponentCachePolicy`, `ComponentCacheScope`, `ComponentCacheStatistics`) и `serializer` (`ComponentSerializer`, `ComponentSerializerType`). Новое: `ComponentCachePolicy.DISABLED`, `ComponentCacheStatistics.lookups`, `ComponentTemplate.values(map)`. Импорты нужно поправить.
- `api.updates` разложен на `catalog` (`CatalogSource`, `ProductDescriptor`, `ProductId`, `UpdateChannel`) и `state` (`UpdateState`, `UpdateSnapshot`, `UpdatePlanSnapshot`, `BlockedReason`, `UpdateActor`); `api.placeholders` на `adapter` и `cache`. Импорты нужно поправить.
- `api.actions` стал `api.scenario.actions`, `api.requirements` стал `api.scenario.requirements`, калькулятор вынесен в `api.scenario.expression`. `ChatPalette` и `ChatTone` перенесены в `api.chat.style`. Несовместимое изменение API: нужно поправить импорты.

### События по папкам
- Пакет `api.events` разложен: корень (`Event`, `ResultedEvent`, `EventDescription`), `bus` (`EventService`, `EventScope`, `DispatchMode`), `listener` (`Listener`, `EventHandler`, `EventPriority`, `EventSubscription`, `EventListenerRegistration`, `EventListenerBuilder`, `subscribe`), `inspect` (`EventInspector`, `EventInfo`, `ListenerInfo`), `inspect.history` (`EventDay`, `EventHour`), `inspect.trace` (`EventTrace`), `player`. В `core.events`: `registration`, `player`, `inspect`, `inspect.history`, `inspect.trace`.
- `EventInspector`: убраны заглушки по умолчанию у `hourly`, `asyncQueued`, `exportHistory`. Новое: `on<E>().listenOnce { }` слушает только первое событие. Несовместимое изменение API.

### Новая система действий и требований
- `api.actions` переписан: `Action.run` возвращает `ActionResult` (`Done`, `Stopped`, `Failed`). Ошибка шага откатывает прошлые шаги через `ActionContext.onUndo`; блок `attempt` ловит ошибку и показывает сообщение.
- `api.checks` заменён на `api.requirements`: `Requirement` отвечает `Verdict.Met` или `Verdict.Unmet(reason)`, значения берутся из контекста, плейсхолдеров pnLibrary и PlaceholderAPI. Списание денег — действие `charge` (`give`, требование `balance`), слова добавляет сама библиотека для всех плагинов.
- Выражения: `Expression` (математика, логика, функции, плейсхолдеры), требование `expression`, текст `{= ... }`, действия `set`, `call`, `random`, обработчики ошибок по коду в `attempt`, именованные `define`/`defineRequirement`/`defineFunction` (на модуль или на библиотеку, с политикой конфликтов).
- Удалены `ActionNode`, `ActionText`, форматы DeluxeMenus, шаблоны `run`, `group`, `require` со старыми полями, `InternalPnLibraryApi`. Несовместимое изменение API.

### Убран Activity API
- Удалены `PnLibrary.activity`, `ActivityService`, `ActivityEvent` и остальные типы пакета `api.activity`. Вместо них `PnLibrary.observability`:
  `capture { }` для события, `failure(error)` для ошибки, `recent(query)` для чтения. Несовместимое изменение API.
- Слои игроков: `AudienceSender` и `PlayerCapability` вынесены в отдельные файлы, `PlayerEffects.kt` разделён на `PlayerEffect` и `PlayerParticle`.

### Настройки библиотеки без файлов
- `config.yml` и `downloads.yml` больше не читаются. Все настройки меняются командой `/pn settings list|get|set|reset` (право `pnlibrary.settings`)
  и в меню «Настройки» → «Параметры библиотеки»; значения лежат в `data/settings.json`, список настроек один (`SettingsRegistry`), описание в
  [docs/settings.md](docs/settings.md).
- Старые файлы при первом запуске переносятся в `settings.json` и переименовываются в `*.yml.old`; выбор `/pn deps auto` главнее `downloads.yml`.
- Новые настройки: время проверки обновлений (`updates.check-interval`) и таймауты скачивания обновлений. Настройки загрузок и часового пояса
  работают сразу, остальные после перезапуска.

### Справка в папке плагина
- В `plugins/pnLibrary` библиотека сама пишет `Помощь.txt` (помощь, Discord, репозиторий, главные команды), `Настройки.txt` (все настройки с
  текущими значениями, описаниями и командой для изменения) и `Команды.txt` (все команды `/pn` и права, в том числе `/pn settings`, `/pn configs`,
  `/pn support`, `/pn gui`, `/pn deps`, подкоманды `/pn updates` и `/pn update`). Они создаются заново при каждом запуске, `Настройки.txt` ещё и после
  изменения настройки. Если JVM не умеет русские имена файлов, они называются `HELP.txt`, `SETTINGS.txt` и `COMMANDS.txt`.
- Список команд и прав один, в коде (`LibraryCommandGuide`); тест сверяет его с правами в `plugin.yml`. В `plugin.yml` добавлены права
  `pnlibrary.configs`, `pnlibrary.configs.edit`, `pnlibrary.servers` и `pnlibrary.currency.list` (по умолчанию у операторов, как и раньше).
- В файлы настроек плагинов больше не пишется заметка с Discord; в старых файлах она удаляется при следующей записи.

### Настройки в меню
- Переключатели везде показываются словами `включено` и `выключено` (меню, `/pn settings`, `Настройки.txt`); в команде принимаются также
  `вкл`, `выкл`, `да`, `нет`, `true`, `false`, `on`, `off`. В подсказке переключателя вариант по умолчанию помечен серым курсивом `(по умолчанию)`,
  отдельной строки «По умолчанию» у переключателя больше нет.
- Часовой пояс выбирается в меню из одного постраничного списка всех поясов. У каждого пояса видно время там сейчас (`21:45`) и смещение
  (`UTC+03:00`, с учётом летнего времени). Кнопки: сортировка (по смещению UTC или по алфавиту), «Популярные» (Москва, Лондон, Нью-Йорк и другие),
  «Поиск» и «Ввести в чате». Поиск: `/pn settings pick <ключ> [часть названия]`, например `new york`. Настройки с длинным списком значений
  описываются типом `CHOICE` и `ChoiceSource`; Tab в `/pn settings set` подсказывает пояса.
- В меню часовой пояс переключается кликом, как настройка с вариантами в конфигах: клик следующий пояс из «Популярных», правый клик предыдущий,
  Shift + клик открывает полный список. В подсказке окно значений со строками `▲ выше ещё N` и `▼ ниже ещё N`, смещение и время там сейчас;
  пояс вне популярных стоит первой строкой. `ChoiceSource.cycle` задаёт набор для прокрутки.
- Числа: `@ConfigStep(шаг, единица)`, `@ConfigDuration(TimeUnit)` (в меню «1 ч 30 мин», шаги секунда/минута/час, ввод `90m`, `1h30m`).
  Колёсико мыши открывает редактор числа: шаги вниз и вверх разного размера, «Минимум», «Максимум», ввод в чате.
- Тексты: `@ConfigHint(format, examples, values, strict)`; со `strict` текст становится списком выбора. Длинный список листается
  отдельным экраном (Shift + клик).
- В списках: кнопки страниц `◀ Страница N` / `Страница N ▶`, номер страницы и диапазон на средней кнопке. В карточке файла виден полный класс.

### Зависимости
- У внешней зависимости несколько источников без контрольной суммы: `Dependencies.plugin(имя, версия, listOf(url1, url2))`,
  `ExternalPluginDependency.Builder.mirror(url)`, `FileDownload.Builder.mirror(url)`. Файлы с разных адресов сверяются; при расхождении загрузка отклоняется.
  Единственный адрес запоминает SHA-256 первой загрузки: если файл по нему изменился, нужно подтверждение администратора.
- Автозагрузка недостающих плагинов включена по умолчанию (у зависимости с прямой ссылкой). Выбор администратора лежит в данных
  (`downloads/preferences.state`), а не в конфиге, и главнее `downloads.yml`; переключатель есть в меню настроек и в `/pn deps auto on|off`.
- Чего не хватает: сообщение в консоли со ссылками и кодом. `/pn deps` показывает список, `/pn deps install <код>` скачивает сразу, даже при выключенной автозагрузке.
  Право `pnlibrary.deps`.

### Конфигурации
- Регистрация полиморфных типов по адресу: `.into("плагин" | "плагин/модуль" | "плагин/модуль:путь" | ConfigAddress | ManagedConfig)`.
  `ConfigTypeAccess` и `.access(...)` удалены. Регистрация живёт и умирает с модулем регистранта.
- Защита: нельзя занять слова базового типа и хозяина файла; хозяин ограничивает расширения `extensions { allow/deny/denyAll }`.
- `ConfigOptions.unknownTypes` (`FAIL`/`SKIP`): при `SKIP` элемент списка с незнакомым `type` пропускается и остаётся в файле;
  запись заблокирована, пока есть пропущенные. Ожидающие конфиги перечитываются при регистрации типа.
- Идентификаторы `плагин/модуль:путь` (`ConfigId`, `ConfigAddress`, `ConfigInfo`) и каталог:
  `library.configurations.all/find/get/require/byModel/types`, `library.plugins.getById/requireById`, `PluginRegistration.configs()`,
  `ConfigScope.list/get/require`, `ManagedConfig.id/info`.
- `ConfigOptions.root` (`PLUGIN`, `PLUGINS`, `SERVER`) и `ConfigOptions.readOnly`.
- Команда `/pn configs [страница] | info | reload | validate | types`, право `pnlibrary.configs`.
- Файл по любому абсолютному пути: `scope.yamlAt(Path)` (корень `ConfigRoot.EXTERNAL`, ID `плагин/модуль:@external/<путь>`).
  Путь может прийти из собственного конфига плагина. Файл считается своим: создаётся и перезаписывается, секреты скрываются в отчётах.
- Один класс `PluginAccess` (`ownerOnly()`, `shared()`, `only(...)`, `builder()`) вместо `PlaceholderAccess`, `CurrencyAccess`
  и `ConfigExtensionPolicy`; `configs.extensions { }` теперь тоже на нём.
- Правка настроек в игре: аннотация `@ConfigEditable(title, description, apply)`, `ManagedConfig.settings` и `edit(path, text)`,
  `ConfigApply` (`LIVE`, `RELOAD`, `RESTART`). Секреты (`@ConfigSecret`) не показываются. Команды `/pn configs settings <id>` и
  `/pn configs edit <id> <путь> <значение>` (право `pnlibrary.configs.edit`), страница «Конфигурации» в `/pn gui`: плагины → модули → файлы (или все файлы плагина с названием модуля), с переключением и страницами.
- Колбэки: `ManagedConfig.onChanged(...)` (чтение, запись, правка) и `onReloadRequired(...)` (изменена настройка, которой нужно
  перечитывание или перезапуск); принимают несколько обработчиков, ошибка одного не мешает другим. `ConfigInfo.pendingApply`.
- Адрес собирается из частей, строки не нужны: `ConfigAddress.plugin("shop").inModule("main").inFile("a.yml")`; `into` принимает ещё `PluginId`, `ConfigScope`, `ConfigInfo`, `ManagedConfig`.
- Адрес `*` (`ConfigAddress.everywhere()`, в билдерах `.everywhere()`): слово типа и сериализатор можно добавить во все файлы всех плагинов.
- Сериализаторы в чужих файлах: `scope.serializerFor(serializer).into(адрес).priority(n).register()`, список `configurations.serializers()`.
  Свои сериализаторы модуля главнее добавленных, хозяин ограничивает `extensions { }`, чужим файлам нельзя подменить примитивы
  и типы `java.`/`kotlin.`. Сериализаторы читаются при каждом чтении файла, поэтому добавление после `yaml(...)` тоже работает.

### Плейсхолдеры
- Дерево с аргументами (`player`, `param`, `choice`), адрес `плагин/модуль:путь`, генерация веток `from<R>`, `table`, `list`,
  форматы по четырём уровням и свои форматы модуля, доступ `access`, запись `update`, публикация в PlaceholderAPI из той же
  регистрации. Команда `/pn placeholders`, страница в `/pn gui`. Подробности: docs/placeholders.md.

### Обновления
- `updates.yml` и список `ignored` удалены. Автообновление включено по умолчанию, меняется командой или в меню:
  `/pn updates set <плагин> auto on|off` и `/pn updates set all auto on|off` (новый метод `UpdateService.setAutomaticDownloadForAllPlugins`).
  Отложить обновление можно только паузой до 7 суток. Старый файл переименовывается в `updates.yml.old`.
- Напоминания об обновлении идут по своему интервалу (6 часов по умолчанию, в коде от 1 минуты до 6 часов) в консоль и
  в чат администраторам (Bukkit, Velocity, BungeeCord), без обращения к GitHub. Администратор их не отключает.
- Обновление библиотеки сохраняет вариант сборки: если стоит `universal.jar` (его кладёт установщик в плагинах), берётся новый
  `universal.jar`, а не файл платформы.
- Релиз: `pnLibrary-<версия>-universal-full.jar` (Kotlin и Adventure внутри, без скачивания при старте) загружается в релиз
  GitHub и попадает в `checksums.sha256`.
- Необязательные библиотеки (драйверы баз, компилятор ECJ) скачиваются через Libby только при первом использовании
  (`LibraryLoader.loadOptional`, `OptionalLibrary`), а не на старте.

### Резервные копии перед обновлением
- Копии с типом и именем (`BackupKind`: конфигурация, база данных, данные игроков, прочее; `PluginUpdateRequest.Builder.backup`).
  Наборы хранятся 30 дней, при 500 МБ и больше в консоль идёт предупреждение; удалить: `/pn updates backups delete <id>`
  (`UpdateService.deleteBackup`).
- `PluginUpdateRequest.Builder.backupExport`: плагин сам выгружает данные перед обновлением; файл хранится с набором,
  откат его не восстанавливает.
- Базы по аннотациям: MySQL/MariaDB, PostgreSQL (`DatabaseKind.POSTGRESQL`: таблицы текущей схемы с индексами, ограничениями
  и последовательностями) и MongoDB (`DatabaseKind.MONGODB`, `DatabaseRole.URI`). Аннотации: `@BackupDatabase`, `@DatabaseField`, `@BackupTable`.
  Вернуть базу можно кнопкой в меню отката (с предупреждением о потере данных) или `/pn updates backups restore` (`UpdateService.restoreDatabase`).
- Только нужные таблицы или коллекции: `@BackupDatabase(..., tables = [...])` и `@BackupTable`; в имени работает `*`. Без них копируется вся база.
- Несколько баз в одном классе: поля привязываются к базе через `database = "ID"` у `@DatabaseField` и `@BackupTable`,
  отдельные классы и аннотации на классе не нужны.
- Меню копий идёт по плагинам и датам. Выдать файлы копии вместо возврата: кнопка «Выдать файлы» или `/pn updates backups files <id> <плагин>`.

### Ошибки, безопасность, скорость
- **Ошибки не теряются.** Сбои журнала, статистики, задач, закрытия платформ, валют и плейсхолдеров пишутся в логгер на уровне
  `ERROR`/`CRITICAL`; запись уровня `ERROR` и выше без исключения тоже уходит в трекер ошибок (FastStats).
- **Срок хранения записей.** `ObservationScope.keepFor(Duration)` задаёт автор плагина в коде, срок перекрывает срок уровня.
  В отчёте поддержки у записей есть `timeUtc` и `expiresUtc`. Раздел «Сообщить о проблеме» в docs/diagnostics.md.
- **Безопасность.** Права на опасные действия по связи проверяет получатель, а не вызывающий; `link.key` и файлы копий
  создаются с правами владельца; каталог релизов берёт raw/API раньше jsDelivr; хосты скачивания ограничены списком;
  имя JAR платформы должно начинаться с `pnLibrary-`; битый `journal.json` не блокирует обновления и откаты;
  `@ConfigSecret` работает внутри списков и карт (`by-name.*.pin`).
- **Старый Adventure.** На серверах, где Adventure слишком старый для MiniMessage (например Paper 1.16.5), MiniMessage заменяется
  упрощённым разбором (цвета, `#rrggbb`, декорации, `reset`, `br`, экранирование `\<`), в лог пишется одно предупреждение.
- **Копии баз.** PostgreSQL возвращается одной транзакцией, копируются enum-типы, параметры последовательностей и внешние
  ключи таблиц вне маски; MySQL сохраняет переводы строк в литералах и режет `INSERT` по размеру; Mongo-индексы сохраняют
  `partialFilterExpression` и `collation`.
- **Скорость.** Журнал пишется пачками, буфер логов не очищает повторы заново, групповой коммит и кеш закодированных
  транзакций в валюте, индекс плейсхолдеров, кеш шаблонов `Wildcards`, история событий без общей блокировки, предрассчитанные
  триграммы в поиске перевода, одна проверка HMAC на кадр связи, журнал при входе админа собирается не на главном потоке.
- **Лёгкий universal на Bukkit и BungeeCord.** Libby не скачивал MiniMessage и plain-сериализатор (на BungeeCord ещё gson-сериализатор), плагин падал при старте с `NoClassDefFoundError: net/kyori/adventure/text/minimessage/MiniMessage`. Эти библиотеки добавлены в списки загрузки, тест `RuntimeLibrariesTest` следит за списками.
- **Ссылки поддержки.** `PnLibraryIdentity.SUPPORT_URL` и `REPOSITORY_URL` больше не `const`: компилятор не копирует их в плагины, они читаются из запущенной библиотеки, и новая ссылка доходит до плагинов без пересборки (после одной пересборки на этом API). В карточку уведомления об обновлении добавлена строка поддержки.
- **Vault.** Пока Essentials не загрузил настройки, подключение валюты Vault тихо откладывается до следующего события, а не пишет SEVERE на каждой попытке.
- **Публикация.** Без ключа подписи `publishToMavenLocal` работает (артефакты неподписанные); подключение без публикации
  описано в integration/README.md.

### Прочее
- **Статистика.** Почасовые срезы в журнал, `/pn stats`, страница меню.
- **Модули.** Из `core` вынесены `link`, `placeholders`, `events`, `upload`, `journal`: границы между функциями проверяет
  компилятор. Имена пакетов не менялись.
- **Исправлено.** Folia: библиотека стартует с `link.key`, задачи вышедшего игрока снимаются с учёта. Карта сети не превышает лимит кадра.
  Прокси не затирает нечитаемый `link.key`. На Paper плагины работают из `plugins/.paper-remapped`, папка бэкапа конфигов
  больше не отклоняется. Отчёт поддержки не содержит значений `@ConfigSecret` и `excluded-paths`, а сбой скрытия прячет значение целиком.
  Пулы данных меню закрываются при остановке.
- **Структура.** `core/commands` разнесён по функциям, длинные файлы и методы разбиты, классы с `Impl`/`Manager`
  переименованы по смыслу. Старый API не менялся: плагины на `0.0.1-dev.6` работают без пересборки.
- **Удалено.** `RemotePolicySettings.Builder.sha256(...)` и проверка хеша политики: с закреплённым хешем политику нельзя менять удалённо.
  Ни один плагин pnFolder его не вызывал. Уровень API не менялся.

## Политики без JDK

- Если на сервере нет `javac` (сервер запущен на JRE), библиотека докачивает компилятор ECJ и собирает им политику.
  Грузит его тот же Libby, что и Kotlin, на старте: для Java 8–16 ECJ 3.26.0, для Java 17+ ECJ 3.43.0,
  SHA-256 зашит при сборке. На серверах с JDK ничего не качается. Сбой из-за JDK или сети пишется предупреждением и не уходит в телеметрию.

## Логгер модуля

- `PnLogger` получил флаг `record`: `info(msg, false)`, `warning(msg, false)`, `error(msg, ex, false)` пишут только в
  консоль и не попадают в чёрный ящик (буфер диагностики, журнал, отчёт об ошибках). Короткие формы пишут везде, как раньше.
- Новые перегрузки `warning(msg, error)` и `error(msg)`; `warning` с исключением сохраняет стек в отчёте.
- `debug(msg)` и `LogLevel.DEBUG`: строка уходит только в чёрный ящик, консоль молчит. Заменяет `Level.FINE`.
- Общий метод `log(level, msg, error, record)` теперь единственный обязательный у `PnLogger`.

## Конфигурации плагинов: @ConfigSecret

- Аннотация `@ConfigSecret` на поле класса настроек гарантирует, что значение не попадёт в отчёт, и ничего не нужно прописывать в `config.yml` библиотеки.
  Работает для вложенных разделов. Файлы, зарегистрированные через `configurations.scope(...).yaml(...)`, теперь сами попадают в отчёт плагина.


## config.yml

- В шапке `config.yml` на русском и английском перечислены все команды и права; тест сверяет её с `plugin.yml`.
- `privacy` по умолчанию `false`: личные данные в отчёте показываются, скрыть их можно, поставив `true`.
- В комментарии к `excluded-paths` пример: путь для всех файлов или `плагин/файл.yml:путь` для одного файла одного плагина.
- Параметр `journal-join-notice` убран: сводка недавних проблем при входе администратора показывается всегда.
  Старый ключ в существующем `config.yml` библиотека удалит сама и предупредит.

## Команды: свободный текст

- `ArgumentType.greedyString()` забирает остаток строки одним значением; на Paper клиент получает `greedyString` Brigadier.
- `acceptsExtraArguments()` на узле и на корне команды: слова, которых нет среди дочерних узлов, не дают `UnknownArgument`,
  а доходят до обработчика в `context.arguments`. Клиент Paper не отклоняет их заранее.
- Алиасы команды разрешены на любом языке (`обмен`); имя команды и литералы по-прежнему `a-z0-9:_-`.

## Проверка API

- `tools/api/check-api-generation.ps1` для версии `-dev` только предупреждает об удалённых объявлениях: опубликованного API
  ещё нет, и ломающие правки не требуют повышения поколения. Для релизных версий правило прежнее.

## События

- `@EventHandler(ignoreCancelled)` и `EventListenerBuilder.ignoreCancelled()` переименованы в `ignoreDenied`: события в pnLibrary
  не отменяются, а отклоняются результатом. Устаревший псевдоним удалён, пока API не опубликован.

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
  `OnlinePlayer` и `LibraryAudience`.
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
- `OnlinePlayer`, `LibraryAudience`, `PlayerEffect`, `PlayerParticle`, `ItemQuery` → `api.audiences`.
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
- Подписка через билдер: `scope.on<Event>().priority(...).ignoreDenied().listen { ... }`.

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
