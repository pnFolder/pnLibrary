# Встраиваемый установщик pnLibrary (Bukkit)

Маленькая библиотека, которую плагин встраивает в свой jar. Сама она от pnLibrary не зависит, поэтому может
скачать и установить pnLibrary раньше, чем плагин обратится к её API. Так администратору не нужно ставить
pnLibrary вручную.

## Подключение

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-bootstrap-bukkit:<версия>")
}

tasks.shadowJar {
    relocate("org.pnfolder.pnlibrary.bukkit.bootstrap", "your.plugin.libs.pnlibrary.bootstrap")
    relocate("com.google.gson", "your.plugin.libs.gson")
}
```

## Вызов

Вызовите установщик до любого обращения к pnLibrary:

```java
@Override
public void onLoad() {
    if (!PnLibraryBootstrap.ensureInstalled(this, "2.2.0")) {
        getServer().getPluginManager().disablePlugin(this);
    }
}
```

В `plugin.yml` укажите `softdepend: [pnLibrary]`, а не `depend`: с жёсткой зависимостью Bukkit не загрузит плагин
раньше, чем установщик успеет отработать. Классы, которые используют pnLibrary, не должны попадать в путь вызова
установщика, пока установка не прошла.

## Что проверяется

- Скачиваются только файлы релизов GitHub по HTTPS с указанными размером и SHA-256.
- Размер и SHA-256 скачанного файла сверяются.
- Проверяется `plugin.yml` внутри jar.
- Файл кладётся в `plugins` атомарным переносом, если файловая система его поддерживает.
