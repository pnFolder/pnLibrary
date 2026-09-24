# Example remote policy

This project is built separately from the main plugin and uploaded as a remote policy artifact.

Build it with:

```text
gradlew :examples:remote-policy:build
```

The resulting JAR is compiled for Java 8. Upload it to the URL used by `RemoteCheckOptions`.

The example allows plugin version `2.4.0` and newer. Older versions are denied:

```text
Установлена версия 2.3.1, требуется 2.4.0. Скачайте новую версию плагина.
```
