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
