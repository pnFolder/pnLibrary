# Example remote policy

The Java source in this project is an example remote policy. Publish the raw `.java` file at an HTTPS URL.

Build it with:

```text
gradlew :examples:remote-policy:build
```

The host plugin points directly to a raw `.java` file. The package and class name
are read from the source automatically; they are not written in the host plugin.

The host plugin registers it with pnLibrary:

```kotlin
val plugin = library.plugins.register(this)
plugin.registerModule("pncase") { builder ->
    builder.remotePolicy { policy ->
        policy.source("https://raw.githubusercontent.com/pnFolder/pnRemotePolicies/main/pnCase/RemotePolicy.java")
        policy.checkEvery(Duration.ofHours(6))
        policy.onDeny(DenyAction.DISABLE_MODULE)
    }
}
```

The example allows plugin version `2.4.0` and newer. Older versions are denied:

```text
Установлена версия 2.3.1, требуется 2.4.0. Скачайте новую версию плагина.
```
