# Code-first конфигурации pnLibrary

Плагину больше не требуется наследоваться от сериализатора или писать собственный
codec. `PluginContext.configs` создаёт типизированные YAML из обычных Java/Kotlin
классов. `CodeFirstYaml<T>` остаётся низкоуровневым расширением для нестандартных
форматов.

```java
@ConfigComment("Main tiAuth configuration.")
public final class MainConfig {
    @ConfigComment("Available values: LEGACY, MINIMESSAGE")
    public Serializer serializer = Serializer.LEGACY;

    @ConfigNewLine
    public Servers servers = new Servers();

    public static final class Servers {
        @ConfigComment("Enable the virtual authorization server.")
        public boolean useVirtualServer = false;

        @ConfigRange(min = 1, max = 65535)
        public int virtualServerPort = 65535;
    }
}

ManagedConfig<MainConfig> main = context.getConfigs().yaml(
    "config.yml", MainConfig.class, MainConfig::new
);
MainConfig settings = main.loadValue();
```

Kotlin:

```kotlin
class MainConfig {
    @ConfigComment("Available values: LEGACY, MINIMESSAGE")
    var serializer = Serializer.LEGACY

    @ConfigNewLine
    var servers = Servers()
}

val main = context.configs.yaml("config.yml", ::MainConfig)
val settings = main.loadValue()
```

Аннотации: `@ConfigComment`, `@ConfigKey`, `@ConfigIgnore`, `@ConfigNewLine`,
`@ConfigOrder`, `@ConfigRange`, `@ConfigNotBlank`, `@ConfigPattern`.

Поведение синхронизации задаётся явно:

```java
ConfigOptions options = ConfigOptions.builder()
    .missingFile(MissingFilePolicy.CREATE)
    .missingValues(MissingValuePolicy.ADD)
    .unknownValues(UnknownValuePolicy.PRESERVE)
    .comments(CommentPolicy.ADD_MISSING)
    .backups(true)
    .build();

ManagedConfig<MainConfig> main = context.getConfigs().yaml(
    "config.yml", MainConfig.class, MainConfig::new, options
);
```

- `MissingFilePolicy`: создать отсутствующий файл или завершить загрузку ошибкой;
- `MissingValuePolicy`: добавить новые параметры, использовать default только в памяти или считать отсутствие ошибкой;
- `UnknownValuePolicy`: сохранить лишние параметры, удалить их или считать ошибкой;
- `CommentPolicy`: сохранить файл как есть либо добавить отсутствующие комментарии;
- `backups`: создавать ли резервную копию перед автоматическим изменением.

Встроены сериализаторы для enum, массивов, списков, sets, maps, вложенных классов,
`UUID`, `Duration`, `Instant`, Java date/time, `URI`, `URL`, `Path`, `Locale`,
`Pattern`, `BigDecimal` и `BigInteger`. Собственный тип подключается один раз:

```java
context.getConfigs().serializer(WorldPoint.class, new ConfigSerializer<WorldPoint>() {
    public Object serialize(WorldPoint point, ConfigSerializationContext context) {
        return point.world() + ";" + point.x() + ";" + point.y() + ";" + point.z();
    }

    public WorldPoint deserialize(Object raw, ConfigSerializationContext context) {
        return WorldPoint.parse(raw.toString());
    }
});
```

Если сериализатор относится только к одному полю или самому классу, его можно
прикрепить прямо к модели:

```java
public final class MainConfig {
    @ConfigRequired
    public String serverName = "auth-1";

    @ConfigSerializeWith(WorldPointSerializer.class)
    public WorldPoint spawn = new WorldPoint("world", 0, 64, 0);
}

public final class WorldPointSerializer implements ConfigSerializer<WorldPoint> {
    public Object serialize(WorldPoint value, ConfigSerializationContext context) {
        return value.world() + ";" + value.x() + ";" + value.y() + ";" + value.z();
    }

    public WorldPoint deserialize(Object value, ConfigSerializationContext context) {
        return WorldPoint.parse(value.toString());
    }
}
```

`@ConfigSerializeWith` работает на поле и на классе; сериализатор должен иметь
конструктор без аргументов. Приоритет: аннотация поля, аннотация класса, затем
сериализатор из `ConfigScope.serializer(...)`, затем встроенное преобразование.
Контекст содержит полный YAML-путь, raw и generic тип, аннотации поля и
code-defined default. Один сериализатор поэтому может безопасно менять поведение
в зависимости от места использования.
`@ConfigRequired` останавливает загрузку, если ключ отсутствует физически —
default не маскирует ошибку и файл не переписывается.

При закрытии `PluginContext` все его конфигурации автоматически выгружаются из
памяти. Файлы не удаляются.

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

## Вложенные классы

Обычные mutable Java/Kotlin-классы обходятся рекурсивно. Помечать каждый класс
аннотацией не нужно:

```java
public final class Settings {
    public Database database = new Database();
    public List<WorldSettings> worlds = new ArrayList<>();
}
```

Поля, вложенные объекты, массивы, коллекции и map сериализуются автоматически.
`@ConfigSerializeWith` нужен только нестандартному типу, у которого должно быть
особое YAML-представление.

## Enum: подсказки, старые имена и fallback

Для enum библиотека сама добавляет комментарий с допустимыми значениями и
default. Без fallback неправильное значение останавливает загрузку с сообщением
вида `serializer: invalid value MINI; allowed: LEGACY, MINIMESSAGE; default: LEGACY`.

```java
public enum Serializer {
    @ConfigAlias("OLD")
    LEGACY,

    @ConfigAlias("MINI", "MINI_MESSAGE")
    MINIMESSAGE
}

@ConfigDefaultOnInvalid
public Serializer serializer = Serializer.LEGACY;
```

`ConfigAlias` находится на самой enum-константе: соответствие проверяется
компилятором и действует во всех местах, где используется этот enum.
`ConfigDefaultOnInvalid` необязателен: с ним неправильное значение заменяется
значением поля из нового экземпляра настроек, а в консоль выводится warning с
полным путём. Для важных параметров лучше оставить строгий режим.

## Имена YAML-параметров

```java
ConfigOptions options = ConfigOptions.builder()
    .naming(ConfigNamingStrategy.KEBAB_CASE)
    .build();
```

Доступны `AS_DECLARED`, `CAMEL_CASE`, `SNAKE_CASE`, `KEBAB_CASE` и
`UPPER_SNAKE_CASE`. Например, поле `databasePoolSize` станет
`database-pool-size`, а `DATABASE_POOL_SIZE` при `CAMEL_CASE` станет
`databasePoolSize`. Правило применяется рекурсивно ко всем вложенным классам.
`@ConfigNaming(ConfigNamingStrategy.SNAKE_CASE)` на вложенном классе переопределяет
глобальное правило только для его полей. `@ConfigKey("точное-имя")` на конкретном
поле всегда имеет самый высокий приоритет.

## Сценарии действий из конфигурации

> The example below uses the compatibility `PlayerAction` API. For the new
> immutable and polymorphic configuration model, see [ACTIONS.md](ACTIONS.md).

```java
public PlayerActionSequence joinActions = new PlayerActionSequence();
```

```yaml
joinActions:
  actions:
    - type: MESSAGE
      text: '&aДобро пожаловать, {player}!'
    - type: TITLE
      title: '&6Авторизация'
      subtitle: '&fВведите пароль'
      fadeIn: 10
      stay: 70
      fadeOut: 20
    - type: ACTION_BAR
      text: '&eОсталось: {time}'
    - type: SOUND
      sound: ENTITY_PLAYER_LEVELUP
      volume: 1.0
      soundPitch: 1.0
```

```java
context.getActions().execute(player.getUniqueId(), config.joinActions, Map.of(
    "player", player.getName(),
    "time", 30
));
```

Доступны `MESSAGE`, `TITLE`, `ACTION_BAR`, `KICK`, `TELEPORT`, `SOUND` и
`PLAYER_COMMAND`. Сценарий выполняется по порядку и привязан к lifecycle плагина:
после закрытия `PluginContext` новые действия не исполняются. Bukkit поддерживает
весь набор; прокси выполняет только операции, имеющие смысл на прокси.

## Миграции версий

```java
ConfigMigrationPlan migrations = ConfigMigrationPlan.builder("1.4")
    .assumeVersionWhenMissing("1.0")
    .migrate("1.0", "1.1", document ->
        document.rename("database.address", "host"))
    .migrate("1.1", "1.4", document -> {
        document.move("database.host", "storage.mysql.host");
        document.set("storage.poolSize", 10);
        document.remove("legacyOption");
    })
    .build();

ConfigOptions options = ConfigOptions.builder()
    .migrations(migrations)
    .build();
```

В YAML хранится `_config-version: '1.4'`. Если пользователь обновился сразу с
1.0 на 1.4, библиотека найдёт и выполнит всю цепочку `1.0 -> 1.1 -> 1.4`.
То же самое работает для большого разрыва: при обновлении `1.0 -> 1.30` сервер
не нужно запускать на каждой промежуточной версии. За один `load()` библиотека
применит в памяти все зарегистрированные шаги (`1.0 -> 1.1 -> ... -> 1.30`),
затем один раз проверит и сохранит итоговый файл. Версии, в которых схема не
менялась, вообще не требуют шага миграции; допустимы и прямые переходы вроде
`1.12 -> 1.20`.
Миграции выполняются в памяти до десериализации и проверки. При отсутствии пути
или ошибке исходный файл не меняется. После успешной проверки создаётся один
backup и результат записывается атомарно. Выполненная цепочка доступна через
`ConfigLoadResult.appliedMigrations`.
