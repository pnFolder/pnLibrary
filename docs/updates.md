# pnUpdate: система обновлений компонентов

pnLibrary resolves the library and every registered plugin as one compatibility graph. It does not
download a release until the complete target graph supports one pnLibrary API generation. The API
generation remains `1`; semantic plugin versions are independent from it.

## Подключение через Gradle

Для Gradle применяется плагин `ru.privatenull.pnlibrary.component`. Блок `pnComponent` пишется в
`build.gradle.kts`, а не в Java-коде и не в `pom.xml`:

Apply `ru.privatenull.pnlibrary.component` and configure one source of truth:

```kotlin
pnComponent {
    id.set("pnmarket")
    componentVersion.set(project.version.toString())
    apiMinimum.set(1)
    apiMaximum.set(1)
    channel.set("stable")
    managedDependency("pnlibrary", "1.0.0", "pnFolder", "pnLibrary")
    artifact("pnMarket-bukkit.jar", "bukkit", 17, null, 12_345, "<64 hex sha256>")
}
```

Задача `generatePnComponentMetadata` создаёт воспроизводимые UTF-8-файлы:

- `META-INF/pnlibrary/component.json` автоматически попадает внутрь JAR;
- `pn-update.json` прикладывается к GitHub Release;
- `checksums.sha256` содержит контрольные суммы опубликованных файлов.

## Подключение через Maven

`pnComponent { ... }` в Maven не работает. Отдельный Maven-плагин генерации метаданных пока не
выпущен. До его появления Maven-проект регистрирует описание компонента через Java API:

```java
pnContext = pnLibrary.getPlugins().register(this, builder -> builder
    .component(ComponentDescriptor.builder("pncases", getDescription().getVersion())
        .pnLibraryApi(1, 1)
        .managedDependency("pnlibrary", "1.0.0", "pnFolder", "pnLibrary")
        .build())
    .updates("pnFolder", "pnCases", updater -> updater
        .channel(UpdateChannel.STABLE)
        .automaticDownload(true)
        .artifact("(?i)^pnCases-.*\\.jar$", 17))
);
```

Это полноценный runtime-вариант, но он не создаёт `pn-update.json` автоматически. Его необходимо
прикладывать к релизу отдельно. Не добавляйте в `pom.xml` несуществующий
`pnlibrary-component-maven-plugin`: такой артефакт сейчас не опубликован.

Gradle-проект также может передать ту же модель явно:

```kotlin
library.plugins.register(this, "pnmarket") { plugin ->
    plugin.component(
        ComponentDescriptor.builder("pnmarket", version)
            .pnLibraryApi(1, 1)
            .managedDependency("pnlibrary", "1.0.0", "pnFolder", "pnLibrary")
            .build()
    )
}
```

Embedded metadata wins over native fallback. An explicit descriptor must agree with embedded ID,
version, and API range. Required dependencies are checked before commands, events, tasks, services,
or placeholders become visible.

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
