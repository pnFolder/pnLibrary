# pnLibrary Update System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать предсказуемую систему обновлений библиотеки и зарегистрированных плагинов с обязательной проверкой, управляемой загрузкой, графом совместимости и ручным откатом.

**Architecture:** GitHub-каталог формирует кандидатов, resolver строит единый совместимый граф библиотеки и плагинов, orchestrator управляет состояниями, а transaction атомарно подготавливает JAR-файлы. Проверка библиотеки всегда включена; конфигурация управляет только загрузкой и отдельными плагинами.

**Tech Stack:** Kotlin, Java 8 API compatibility, Gradle, JUnit 5, GitHub Releases API, Bukkit/Paper runtime.

**Spec:** `docs/2026-09-27-update-system/PLAN.md`

## Global Constraints

- Пользовательская конфигурация использует термин `plugins`, не `components`.
- Интервал фоновой проверки задаётся разработчиком в коде, не в YAML.
- Проверка обновлений pnLibrary выполняется всегда.
- `automatic-download` управляет только загрузкой и установкой.
- Обновление применяется только для полностью совместимого графа.
- Автоматический откат запрещён; откат выполняет администратор.

## Review Focus

- Устаревший GitHub-кэш не должен влиять на ручную команду.
- Несовместимый плагин должен блокировать обновление библиотеки с понятной причиной.
- Отключённый плагин не скачивается, но его совместимость учитывается.
- Ошибка включения после обновления должна сохранить предыдущий JAR для ручного отката.
- Пустой релиз, HTTP 404 и несовместимая Java должны давать разные сообщения.

---

### Task 1: Конфигурация обновлений

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfiguration.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfigurationTest.kt`

**Interfaces:**
- Produces: `LibraryPolicy(automaticDownload: Boolean)` и `PluginPolicy(enabled: Boolean, automaticDownload: Boolean, pauseUntil: Instant?)`.

- [ ] Добавить падающие тесты на обязательную проверку библиотеки, `plugins.<id>`, паузу до 7 дней и отсутствие пользовательского `check-interval`.
- [ ] Запустить `:modules:core:test --tests *UpdateConfigurationTest` и подтвердить падение.
- [ ] Реализовать новую схему и миграцию старых ключей без сохранения `components` в новый файл.
- [ ] Запустить тесты и подтвердить PASS.
- [ ] Commit: `refactor: define update policies for library and plugins`.

### Task 2: Свежая ручная проверка GitHub

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClient.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateServiceImpl.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClientTest.kt`

**Interfaces:**
- Produces: `releases(..., refresh: RefreshMode)` с `CACHED` и `FORCE_REMOTE`.

- [ ] Добавить тест: `FORCE_REMOTE` игнорирует свежий диск-кэш и возвращает новый GitHub-релиз.
- [ ] Подтвердить падение теста.
- [ ] Реализовать режимы обновления каталога; ручная команда использует `FORCE_REMOTE`, фон — `CACHED`.
- [ ] Добавить отдельные ошибки для HTTP, пустого релиза и отсутствующего JAR.
- [ ] Запустить тесты update/core.
- [ ] Commit: `fix: force fresh catalogue for manual update checks`.

### Task 3: Граф совместимости библиотеки и плагинов

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateResolver.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateResolverTest.kt`

**Interfaces:**
- Produces: единый `UpdatePlan` либо структурированный blocker с plugin ID, текущей версией и требуемым API.

- [ ] Добавить тесты: совместимая библиотека; библиотека + обновление плагина; отсутствие совместимого обновления плагина; несовместимые Java/platform/API.
- [ ] Подтвердить падение новых сценариев.
- [ ] Реализовать выбор полного совместимого набора без частичных обновлений.
- [ ] Запустить resolver tests.
- [ ] Commit: `feat: resolve library updates with dependent plugins`.

### Task 4: Загрузка, установка и ручной откат

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateTransaction.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateStateStore.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateTransactionTest.kt`

**Interfaces:**
- Produces: сохранённый previous JAR, статус `FAILED_AFTER_RESTART`, `rollbackCandidate(product)` и ручной `rollback(product)`.

- [ ] Добавить тесты сохранения предыдущего JAR, ошибки запуска и отсутствия автоматического отката.
- [ ] Подтвердить падение.
- [ ] Реализовать backup и ручную rollback-операцию.
- [ ] Запустить transaction/state tests.
- [ ] Commit: `feat: support administrator-controlled update rollback`.

### Task 5: Команды и понятные сообщения

**Files:**
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommand.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateServiceImpl.kt`
- Test: соответствующие command/orchestrator tests.

**Interfaces:**
- Produces: `/pn update`, `/pn update-confirm`, `/pn update-status`, `/pn update-rollback`.

- [ ] Добавить тесты сообщений AVAILABLE, CURRENT, PAUSED, BLOCKED, FAILED и rollback confirmation.
- [ ] Подтвердить падение.
- [ ] Реализовать пользовательские сообщения только со словами «библиотека», «плагин» и конкретными причинами.
- [ ] Запустить command/core tests.
- [ ] Commit: `feat: expose clear update status and rollback commands`.

### Task 6: Полная приёмочная проверка

**Files:**
- Modify: `examples/acceptance-bukkit/src/main/resources/config.yml`
- Modify: `docs/2026-09-27-update-system/TEST-MATRIX.md`

**Interfaces:**
- Consumes: все предыдущие задачи.

- [ ] Прогнать unit tests модулей API, update, core и Bukkit runtime.
- [ ] Собрать JAR библиотеки и acceptance с generated `component.json`.
- [ ] Проверить сценарий `2.4.0 → 2.5.0`, отключённый плагин, blocker API и ручной rollback.
- [ ] Зафиксировать команды и результаты в `TEST-MATRIX.md`.
- [ ] Commit: `test: verify complete update lifecycle`.

