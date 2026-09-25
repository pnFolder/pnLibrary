# pnUpdate: система обновлений компонентов

pnLibrary resolves the library and every registered plugin as one compatibility graph. It does not
download a release until the complete target graph supports one pnLibrary API generation. The API
generation remains `1`; semantic plugin versions are independent from it.

## Единая регистрация обновлений

Настройка одинакова для Gradle и Maven и находится в обычном Java/Kotlin-коде плагина:

```java
pnContext = pnLibrary.getPlugins().register(this, builder -> builder
    .updates("pnFolder", "pnCases", updater -> updater
        .apiVersions(1, 2)
        .channel(UpdateChannel.STABLE)
        .automaticDownload(true)
        .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17)
        .managedDependency("pneconomy", "2.0.0", "pnFolder", "pnEconomy")
        .pluginDependency("Vault", "1.7.3", "https://github.com/MilkBowl/Vault/releases"))
);
```

`apiVersions(minimum, maximum)` задаёт включительный диапазон API. Для одной версии используйте
`apiVersion(1)`. ID и версия запущенного компонента берутся из нативного описания плагина.

## Метаданные внутри JAR

Для безопасной проверки скачанного, но ещё не загруженного JAR система читает только пассивный
ресурс `META-INF/pnlibrary/component.json`. Классы из скачанного JAR не загружаются. Файл не нужно
писать вручную: его создаёт build-плагин.

Gradle Kotlin DSL:

```kotlin
plugins {
    id("ru.privatenull.pnlibrary.component-metadata") version "2.2.0-beta.2"
}

pnComponentMetadata {
    id.set("pncases") // необязательно, по умолчанию нормализованное имя Gradle-проекта
    apiVersions(1, 2)
}
```

Gradle Groovy DSL:

```groovy
plugins {
    id 'ru.privatenull.pnlibrary.component-metadata' version '2.2.0-beta.2'
}

pnComponentMetadata {
    id = 'pncases'
    apiVersions(1, 2)
}
```

Maven:

```xml
<plugin>
  <groupId>io.github.pnfolder</groupId>
  <artifactId>pnlibrary-component-metadata-maven</artifactId>
  <version>2.2.0-beta.2</version>
  <executions>
    <execution><goals><goal>generate</goal></goals></execution>
  </executions>
  <configuration>
    <componentId>pncases</componentId>
    <apiMinimum>1</apiMinimum>
    <apiMaximum>2</apiMaximum>
  </configuration>
</plugin>
```

Версия автоматически берётся из `project.version`. В Gradle ID по умолчанию берётся из имени
проекта, в Maven — из `artifactId`. Явно задавайте ID только если он отличается от них.

Если в GitHub Release нет `pn-update.json`, pnLibrary использует версию тега, размер и опубликованный
GitHub `sha256` digest подходящего asset. Asset без проверяемого digest автоматически не принимается.

## Прямые загрузки

```java
.downloads(getDataFolder().toPath(), downloads -> downloads
    .file("cases-data", file -> file
        .url("https://example.org/cases.bin")
        .destination(DownloadDestination.DATA_FOLDER, "resources/cases.bin")))
```

`.downloads(...)` доставляет только вспомогательные файлы в каталог данных модуля или кэш pnLibrary.
Пути не могут выйти из разрешённой корневой папки. Другие плагины объявляются через `.depends(...)`,
а обновление самого зарегистрированного продукта — через `.updates(...)`.

Внешний плагин с прямой ссылкой объявляется без ручного размера и SHA-256:

```java
.dependencies(dependencies -> dependencies
    .plugin("Vault", "1.7.3", dependency -> dependency
        .url("https://example.org/Vault.jar")
        .automaticDownload(true)))
```

pnLibrary сначала сравнивает установленную версию с заданным диапазоном. Актуальный плагин не
скачивается повторно. Для отсутствующего или устаревшего плагина скачанный JAR проверяется по
`plugin.yml`, `bungee.yml` или `velocity-plugin.json`: имя и версия внутри файла должны совпасть с
объявленной зависимостью. Проверенный JAR помещается в серверный каталог `plugins/update` и
применяется после перезапуска. Работающий плагин на горячую не удаляется.

Прямые загрузки имеют отдельную политику `plugins/pnLibrary/downloads.yml` и по умолчанию не
выполняются автоматически:

```yaml
downloads:
  enabled: true
  automatic: false
  allowed-hosts:
    - github.com
    - objects.githubusercontent.com
  destinations:
    data-folder: true
    cache: true
```

Для включения необходимо явно установить `automatic: true`. Значение в коде не может обойти
запрет администратора. Для URL принимается только HTTPS и только домен из `allowed-hosts`.
Без `.automaticDownload(true)` декларация остаётся доступной для ручного запуска:

```java
pnContext.getDownloads().downloadNow();
```

Текущее состояние каждого элемента возвращается через `pnContext.getDownloads().snapshots()`.

## Server policy

`plugins/pnLibrary/updates.yml` owns all safety decisions. Component metadata cannot enable a host,
download, new-plugin installation, or restart forbidden there. Defaults check every 30 minutes,
repeat identical console notices after six hours, require SHA-256, disable automatic download,
disable external URLs, disable new plugin installation, and disable automatic restart.

Per-component policy overrides developer defaults without disabling visibility:

```yaml
updates:
  components:
    pncases:
      channel: beta
      automatic: false
      pause: 7d
```

Pause values use `m`, `h`, or `d` (from one minute through 30 days). The absolute expiry is persisted,
so a server restart does not restart the countdown. Legacy `enabled: false` is treated as safe/manual
mode: checks and warnings remain active while automatic downloads and restart stay disabled.

Bukkit operators (or explicitly permitted administrators) receive an in-game notice. Buttons carry
only a short-lived 256-bit nonce bound to player, plan, revision, and action. BungeeCord and Velocity
expose console-only `pnupdate plan`, `pnupdate check`, and `pnupdate stage` commands.
