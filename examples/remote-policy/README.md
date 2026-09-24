# Example remote policy

This project is built separately from the main plugin and uploaded as a remote policy artifact.

Build it with:

```text
gradlew :examples:remote-policy:build
```

The resulting JAR is compiled for Java 8. Upload it to the URL used by
`RemoteCheckOptions.builder(url, "ru.privatenull.pncases.policy.RemotePolicy")`.

The host plugin starts it like this:

```java
RemoteCheckOptions options = RemoteCheckOptions.builder(
        "https://example.com/releases/pncases-policy.jar",
        "ru.privatenull.pncases.policy.RemotePolicy")
    .intervalTicks(6L * 60L * 60L * 20L)
    .listener(new RemoteCheckListener() {
        @Override public void denied(RemoteCheckContext context, String reason) {
            getLogger().warning(reason);
        }
    })
    .build();

RemoteCheckRunner.schedule(this, options);
```

The example allows plugin version `2.4.0` and newer. Older versions are denied:

```text
Установлена версия 2.3.1, требуется 2.4.0. Скачайте новую версию плагина.
```
