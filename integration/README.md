# Подключение плагина к pnLibrary

Эта папка для тех, кто пишет плагин на pnLibrary: человека или нейросети. Здесь всё, что нужно, чтобы плагин
подключился к библиотеке и получал обновления.

| Файл | Для кого |
|---|---|
| [README.md](README.md) | человек: пошаговая настройка |
| [AGENTS.md](AGENTS.md) | нейросеть: правила, что делать и чего не делать (можно скопировать в репозиторий плагина) |
| [template/.pnlibrary/](template/.pnlibrary) | заготовка папки для репозитория плагина |

## Что нужно плагину, чтобы работать с pnLibrary

1. **Зависимость в сборке** (только API, реализация живёт на сервере):

   ```kotlin
   dependencies {
       compileOnly("io.github.pnfolder:pnlibrary-api:<версия>")
   }
   ```

2. **Зависимость в `plugin.yml`**: `depend: [pnLibrary]`.
3. **Регистрация плагина** при запуске: `PnLibraryProvider.get().plugins.register(this)`.
4. **Папка `.pnlibrary` в корне репозитория плагина.** Это единственный источник правды о версиях: библиотека читает из
   неё список релизов и описания изменений. Заготовка лежит в [template/.pnlibrary](template/.pnlibrary).
5. **Обновления**, если плагин хочет получать их через pnLibrary:

   ```java
   library.getPlugins().register(this)
       .updates("Владелец", "РепозиторийПлагина", updater -> updater
           .channel(UpdateChannel.STABLE)
           .artifact("(?i)^MyPlugin-[0-9].*\\.jar$", 8));
   ```

   Если основная ветка называется не `main`, добавьте `.branch("master")`; если папка не `.pnlibrary`, добавьте
   `.catalogPath("updates")`.

## Какие модули подключать

Зависимость `pnlibrary-api` небольшая: контракт (интерфейсы и модели) без логики. Реализация живёт на сервере в JAR
pnLibrary, поэтому в плагин она не попадает.

| Модуль | Что даёт | Когда нужен |
|---|---|---|
| `pnlibrary-api` | логгер, наблюдения, плейсхолдеры, обновления, задачи, события, меню | всегда |
| `pnlibrary-config-api` | аннотации конфигов (`@ConfigComment`, `@BackupDatabase` и другие) | приходит вместе с `pnlibrary-api` |
| `pnlibrary-minecraft-localization-api` | локализация | приходит вместе с `pnlibrary-api` |
| `pnlibrary-common` | общие помощники | приходит вместе с `pnlibrary-api` |
| `pnlibrary-currency-api` | валюты | если плагин работает с валютой |
| `pnlibrary-bukkit-api` | меню, предметы, визуальные сущности Bukkit | если плагин для Bukkit/Paper |
| `pnlibrary-bungee-api`, `pnlibrary-velocity-api` | то же для прокси | если плагин для прокси |

Посмотреть, что именно подтянулось в проект: `./gradlew dependencies --configuration compileClasspath`.

Во время работы плагина нужен сам pnLibrary на сервере. Плагин с `depend: [pnLibrary]` без него не запустится.

## Подключение без публикации (локально)

Так можно собрать библиотеку у себя и подключить к плагину, ничего не публикуя. Нужен клон репозитория pnLibrary.

1. В папке pnLibrary опубликуйте всё в локальный Maven (`~/.m2`). Версия любая, `0.1.0-local` не пересечётся с
   настоящими. Подпись без ключа не нужна:

   ```bash
   ./gradlew publishToMavenLocal -Pversion=0.1.0-local
   ```

2. В плагине добавьте `mavenLocal()` первым:

   ```kotlin
   repositories {
       mavenLocal()
       mavenCentral()
   }

   dependencies {
       compileOnly("io.github.pnfolder:pnlibrary-api:0.1.0-local")
   }
   ```

3. Документация и исходники подтянутся сами: для каждого модуля публикуются `-sources.jar` и `-javadoc.jar`. В
   IntelliJ после синхронизации Gradle работают Ctrl+Q (документация) и Ctrl+B (исходники). Если не появились,
   нажмите «Download Sources and Documentation» в окне Gradle.
4. Чтобы плагин запустился на тестовом сервере, соберите JAR библиотеки и положите его в `plugins`:

   ```bash
   ./gradlew -Pversion=0.1.0-local :distribution:shadowUniversal
   ```

   Файл появится в `distribution/build/libs`.

После правок в pnLibrary повторите шаг 1, затем обновите проекты Gradle в плагине.

## Резервные копии перед обновлением

Библиотека копирует перед обновлением только то, что плагин назвал сам (`PluginUpdateRequest.builder()`):

```kotlin
.configurationBackup(dataFolder.toPath(), "config.yml", "messages.yml")
.backup(BackupKind.DATABASE, "Кланы", dataFolder.toPath(), "clans.db")          // файловая база (SQLite, H2)
.backupExport(BackupKind.DATABASE, "Кланы", "clans.sql") { file -> dump(file) } // своя выгрузка, откат её не возвращает
```

Базы MySQL/MariaDB, PostgreSQL и MongoDB библиотека копирует сама по аннотациям в классе настроек:
`@field:BackupDatabase(DatabaseKind.MYSQL, "Кланы", tables = ["clans", "log_*"])` на поле с подключением,
`@field:DatabaseField(DatabaseRole.HOST)` для полей с другими именами, `@field:BackupTable` для имени таблицы из конфига.
Несколько баз в одном классе: у `@DatabaseField` и `@BackupTable` указывайте `database = "имя базы"`. Пароль помечайте
`@field:ConfigSecret`. Драйвер скачивается при первой копии. Подробности: [docs/updates.md](../docs/updates.md), раздел 5.

## Что лежит в `.pnlibrary`

```
.pnlibrary/
├── releases.json        список выпущенных версий (заполненный образец: template/.pnlibrary/releases.json)
└── changelogs/
    └── <версия>.md      описание каждой версии
```

Для каждой новой версии:

1. добавьте запись в `releases.json` (версия, канал, описание, файлы со ссылкой HTTPS и SHA-256);
2. создайте `changelogs/<версия>.md`;
3. закоммитьте в основную ветку **до** публикации релиза на GitHub;
4. опубликуйте релиз с тегом `v<версия>`.

Полное описание полей: [docs/repository.md](../docs/repository.md). Как проходят обновления:
[docs/updates.md](../docs/updates.md).

## Как проверить, что всё работает

- Запустите сервер и выполните `/pn updates info <плагин>`: карточка должна показать вашу версию и канал.
- Если каталог не найден, в сообщении об ошибке будет полный адрес `releases.json`: по нему видно, какую ветку или
  папку проверить.
