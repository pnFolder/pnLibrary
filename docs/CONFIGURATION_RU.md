# Code-first конфигурации pnLibrary

`CodeFirstYaml<T>` хранит структуру конфигурации в типизированном классе. При
запуске библиотека сериализует экземпляр с настройками по умолчанию, сравнивает
его с существующим YAML и добавляет отсутствующие поля на любой глубине.

Система сохраняет:

- значения, изменённые администратором;
- существующие комментарии и порядок параметров;
- неизвестные ключи сторонних модулей;
- вложенные секции и элементы map;
- исходный файл в резервной копии перед автоматическим дополнением.

Для каждой автоматической синхронизации создаётся отдельная резервная копия;
хранятся пять последних. Вызов `save(value)` намеренно сериализует типизированную
модель целиком и поэтому не предназначен для сохранения неизвестных полей. Если
важны сторонние ключи и ручные комментарии, изменяйте только свою модель и
используйте автоматическое дополнение при `load`, не вызывая `save` без необходимости.

## Модель Kotlin

```kotlin
@Serializable
data class DatabaseConfig(
    @YamlComment("Адрес сервера базы данных.")
    val host: String = "localhost",
    @YamlComment("Порт базы данных.")
    val port: Int = 3306,
)

@Serializable
data class Settings(
    @YamlComment("Включён ли модуль.")
    val enabled: Boolean = true,
    @YamlComment("Настройки базы данных.")
    val database: DatabaseConfig = DatabaseConfig(),
)
```

## Подключение Kaml

pnLibrary не заставляет плагин использовать конкретный сериализатор. Для Kaml
создаётся небольшой [ConfigCodec]:

```kotlin
val managed = CodeFirstYaml(
    file = File(dataFolder, "config.yml"),
    defaults = Settings(),
    codec = object : ConfigCodec<Settings> {
        override fun encode(value: Settings): String =
            yaml.encodeToString(Settings.serializer(), value)

        override fun decode(yamlText: String): Settings =
            yaml.decodeFromString(Settings.serializer(), yamlText)
    },
    logger = logger,
)

val result = managed.load()
val settings = result.value
```

## Простой жизненный цикл

После создания `ManagedConfig<Settings>` файловую работу больше писать не нужно:

```kotlin
managed.load()             // загрузить с подробным результатом
managed.loadValue()        // загрузить и получить Settings
managed.value              // получить значение из памяти
managed.reload()           // перечитать файл
managed.reloadValue()      // перечитать и получить Settings
managed.save()             // сохранить текущее значение
managed.save(newSettings)  // проверить и сохранить новое значение
managed.update { old -> old.copy(enabled = false) }
managed.validate()         // получить список проблем
managed.resetToDefaults()  // вернуть defaults из кода
managed.unload()           // выгрузить из памяти, не удаляя YAML
```

Если `reload()` завершился ошибкой, `managed.value` продолжает возвращать
последнюю корректную конфигурацию.

Несколько файлов объединяются в группу:

```kotlin
val configs = ConfigGroup()
    .add(settingsConfig)
    .add(messagesConfig)
    .add(menusConfig)

configs.loadAll()
configs.reloadAll()
configs.saveAll()
configs.unloadAll()
```

При добавлении нового поля в `Settings` достаточно задать default:

```kotlin
@YamlComment("Интервал сохранения в секундах.")
val saveIntervalSeconds: Long = 300
```

После запуска отсутствующий ключ и его комментарий появятся в старом
`config.yml`. Остальные значения файла останутся прежними.

## Результат синхронизации

```kotlin
val result: ConfigLoadResult<Settings> = managed.load()

result.value       // готовая типизированная конфигурация
result.addedPaths  // например: ["database.port", "saveIntervalSeconds"]
result.backup      // отдельный config.yml.before-sync-*.bak либо null
```

Повторный `load()` идемпотентен: уже добавленные поля не записываются второй раз.

## Проверка значений

```kotlin
val managed = CodeFirstYaml(
    file,
    Settings(),
    codec,
    logger,
    ConfigValueValidator { settings ->
        buildList {
            if (settings.database.port !in 1..65535) {
                add(ConfigProblem("database.port", "должен быть от 1 до 65535"))
            }
            if (settings.saveIntervalSeconds < 10) {
                add(ConfigProblem("saveIntervalSeconds", "не может быть меньше 10"))
            }
        }
    },
)
```

Короткий builder проверок:

```kotlin
val validator = ConfigValidatorBuilder<Settings>()
    .require("database.port", "должен быть от 1 до 65535") {
        it.database.port in 1..65535
    }
    .require("database.host", "не может быть пустым") {
        it.database.host.isNotBlank()
    }
    .build()
```

При ошибке выбрасывается `ConfigValidationException`. Сообщение содержит полный
путь каждого неправильного поля.

## Сохранение из кода

```kotlin
managed.save(newSettings)
```

Перед заменой файла значение сериализуется, повторно декодируется и проверяется.
Запись выполняется во временный файл с последующим атомарным перемещением.

## Использование из Java

```java
ConfigCodec<Settings> codec = new ConfigCodec<Settings>() {
    @Override
    public String encode(Settings value) {
        return settingsSerializer.encode(value);
    }

    @Override
    public Settings decode(String yaml) {
        return settingsSerializer.decode(yaml);
    }
};

CodeFirstYaml<Settings> managed = new CodeFirstYaml<>(
    new File(getDataFolder(), "config.yml"),
    new Settings(),
    codec,
    getLogger(),
    value -> Collections.emptyList()
);

ConfigLoadResult<Settings> result = managed.load();
Settings settings = result.getValue();
```

## Правила безопасности

1. Повреждённый YAML не перезаписывается автоматически.
2. Файл сначала декодируется и проверяется, затем заменяется.
3. Перед каждым дополнением создаётся `*.before-sync-*.bak`; сохраняются пять последних.
4. Неизвестные параметры не удаляются.
5. Ошибка содержит имя файла и путь неправильного значения.
6. Списки сохраняются как единое пользовательское значение; библиотека не
   добавляет элементы внутрь пользовательских последовательностей.
