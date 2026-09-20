# pnUpdate: component updates

pnLibrary resolves the library and every registered plugin as one compatibility graph. It does not
download a release until the complete target graph supports one pnLibrary API generation. The API
generation remains `1`; semantic plugin versions are independent from it.

## Plugin metadata

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

`generatePnComponentMetadata` creates deterministic UTF-8 files:

- `META-INF/pnlibrary/component.json` is embedded through `processResources`;
- `pn-update.json` is published with a GitHub release;
- `checksums.sha256` contains the exact declared artifacts.

At runtime a plugin may provide the same model explicitly:

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

Bukkit operators (or explicitly permitted administrators) receive an in-game notice. Buttons carry
only a short-lived 256-bit nonce bound to player, plan, revision, and action. BungeeCord and Velocity
expose console-only `pnupdate plan`, `pnupdate check`, and `pnupdate stage` commands.
