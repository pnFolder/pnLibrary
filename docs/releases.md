# Выпуск pnLibrary

Полный выпуск запускается через Actions `release`: версия, описание и выбор Maven-публикации.
Сборка проверяет все четыре основных JAR, затем создаёт `distribution/build/libs/release-manifest.json`,
метаданные рядом с JAR и `checksums.sha256`. Версия, канал, API и Java берутся из той же модели,
которая формирует встроенный `META-INF/pnlibrary/component.json`; размеры и хеши — из готовых файлов.

`tools/release/update-catalog.ps1` переносит эти данные в `.pnlibrary/releases.json` и создаёт changelog.
Он проверяет хеши и согласованность метаданных, сохраняет историю и отклоняет перезапись существующей версии.
Workflow коммитит каталог, changelog и версию перед тегом. Список GitHub assets тоже берётся из manifest:
основные JAR, updater, метаданные и суммы.

## Обновление зависимостей

Каждую неделю Dependabot (`.github/dependabot.yml`) проверяет версии в `gradle/libs.versions.toml` и действия в
workflows. Для каждого обновления он открывает отдельный PR в `main`, а обычный CI собирает и проверяет его.
Kotlin, Adventure и JUnit обновляются группами. Крупные версии (major) не предлагаются: рантайм работает на Java 8
и старых серверах, такие переходы делаются вручную. Dependabot не меняет версию самой pnLibrary и не делает релизы:
они идут только через workflow `release`.

Для локальной проверки без публикации:

```powershell
./gradlew.bat check apiCheck :distribution:build
./tools/release/check-release-tools.tests.ps1
```

Maven публикует артефакты, настроенные в Gradle через `mavenPublishing`; список не ограничен API.
Все входы используют `tools/release/publish-central.ps1` (в Actions через общий composite action).
Необходимы secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` и repository variables
`MAVEN_LICENSE_NAME`, `MAVEN_LICENSE_URL`. `SIGNING_PASSWORD` нужен, если ключ защищён паролем.
Локально им соответствуют `ORG_GRADLE_PROJECT_mavenCentralUsername`, `mavenCentralPassword`,
`signingInMemoryKey`, `signingInMemoryKeyPassword`, `POM_LICENSE_NAME`, `POM_LICENSE_URL` с тем же префиксом.

Отдельный workflow `Publish existing version to Maven Central` предназначен для повторной попытки после
сбоя и принимает точный тег, который содержит актуальные release-инструменты. Он проверяет совпадение
тега и версии. Автоматическая Maven-публикация принадлежит только основному release workflow:
обработчик события `release.published` удалён, чтобы один выпуск не загружался дважды.
Оба workflow выполняются последовательно через общую concurrency group `release`.

Настройка workflows не публикует текущую рабочую ветку и не подтверждает работоспособность секретов:
внешняя публикация проверяется только фактическим запуском release workflow.

## Версии и каналы

Коротко: **версия = `X.Y.Z-<канал>.<N>`**, канал в каталоге = слово в версии, выбирается максимальная версия из
разрешённых каналов.

| Канал | Пример версии | Зрелость |
|---|---|---|
| `dev` | `0.1.0-dev.3` | самая ранняя, любые сборки |
| `alpha` | `0.1.0-alpha.1` | ранний тест |
| `beta` | `0.1.0-beta.1` | функции готовы, ищем ошибки |
| `rc` | `0.1.0-rc.1` | кандидат в релиз |
| `stable` | `0.1.0-stable.1` | для всех |

- `<N>` — номер сборки внутри канала для данного `X.Y.Z`. Растёт с каждой сборкой: `dev.10` новее `dev.2`.
- Порядок при одинаковых `X.Y.Z`: `dev < alpha < beta < rc < stable`. Большее `X.Y.Z` новее любого суффикса.
- Версию без суффикса (`0.1.0`) не выпускаем: она новее всех `-stable.N` и ломает порядок.
- Канал пользователя принимает свой канал и более зрелые: `dev` видит всё, `stable` только `stable`.
- Поле `channel` в каталоге должно совпадать со словом версии; тест `RepositoryLayoutTest` это проверяет.
- Канал самой pnLibrary сейчас зафиксирован в коде как `dev` (см. `TODO` в `UpdateSettings`).

Пример каталога со всеми каналами: [.pnlibrary/example/releases.example.json](../.pnlibrary/example/releases.example.json)
(демонстрация лежит отдельно от настоящего каталога, её changelog-файлы в `.pnlibrary/example/changelogs/`).
Для каждой настоящей версии в `.pnlibrary/changelogs/` лежит файл `<версия>.md`.
Имя JAR содержит полную версию с суффиксом: `pnLibrary-0.1.0-stable.1-universal.jar`.

### Для ИИ: чек-лист публикации

1. Выбери канал и следующий `N`: смотри последнюю версию этого `X.Y.Z` и канала в `releases.json`.
2. Версия: `X.Y.Z-<канал>.<N>`, тег `v<версия>`, `channel` совпадает со словом версии.
3. Не перезаписывай существующую версию, скрипт `update-catalog.ps1` это отклонит.
4. Добавь `.pnlibrary/changelogs/<версия>.md` и проверь `./gradlew :modules:updates:test`.
