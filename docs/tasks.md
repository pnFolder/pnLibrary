# Задачи

Планировщик задач плагина — `ModuleContext.tasks` (`TaskScope`). Задача описывается неизменяемым `TaskSpec`.

## Что задаёт TaskSpec

| Поле | Смысл |
|---|---|
| `name`, `description`, `usage` | имя и пояснения для людей, видны в `/pn tasks` и меню (необязательно) |
| `cancellable`, `hidden`, `runnableNow` | можно ли остановить задачу администратору, скрыть её из списка, запустить повторяющуюся сразу |
| `key`, `conflictPolicy` | ключ внутри плагина и что делать, если такой ключ уже есть |
| `execution` | на каком потоке и контексте платформы выполняется |
| `delay` | задержка до первого запуска |
| `interval` | период повтора; `null` — разовая задача |
| `conditions` | условия, которые должны выполниться перед каждым запуском |
| `cancellationConditions` | если любое выполнилось, задача отменяется |
| `tags` | метки для поиска (`TaskQuery`) |
| `action` | что выполнять; получает `TaskContext` |

`TaskContext` показывает номер запуска (`runNumber`), плановое и фактическое время (`scheduledAt`, `startedAt`) и умеет `cancel()` — остановить все будущие запуски.

## Правила

- Запуск: `tasks.schedule(spec)` возвращает `TaskHandle`; найти — `get(id)`, `getByKey`, `query(TaskQuery)`; отменить — `cancel`, `cancelAll`. Команда администратора: `/pn tasks` (право `pnlibrary.tasks`).
- Задачи плагина отменяются вместе с ним при выключении.
- Действие должно быть коротким: долгую работу выносите с главного потока через `execution`.
- Настройки сервиса задач — `TaskServiceSettings`.

## Три способа описать задачу

Результат один и тот же — `TaskSpec`; выбирайте, что удобнее.

**1. Kotlin DSL**
```kotlin
tasks.schedule {
    name = "Рассылка"
    interval = Duration.ofSeconds(30)
    maxLag = Duration.ofSeconds(10)
    tolerateFailures = 2
    run { ctx -> broadcast() }
}
```

**2. Билдер** — подходит, когда спеку нужно собрать заранее и использовать несколько раз
```kotlin
val spec = TaskSpec.builder()
    .name("Рассылка")
    .interval(Duration.ofSeconds(30))
    .whenLate(Duration.ofSeconds(5)) { lag -> log.warn("Опоздали на ${lag.lag.seconds} с") }
    .action { broadcast() }
    .build()
tasks.schedule(spec)
```

**3. Класс** — наследник `ScheduledTask`, всё переопределяется по необходимости
```kotlin
class Autosave(private val world: String) : ScheduledTask() {
    override val name = "Автосохранение $world"
    override val key = "autosave:$world"
    override val conflictPolicy = TaskConflictPolicy.REPLACE
    override val interval: Duration? = Duration.ofMinutes(5)
    override val maxLag = Duration.ofSeconds(30)

    override fun shouldRun() = server.isRunning
    override fun onLag(lag: TaskLag) = if (lag.missedRuns > 3) LagResponse.SKIP else LagResponse.RUN
    override fun run(context: TaskContext) = save(world)
}
tasks.schedule(Autosave("world"))
```

## Подзадачи

Подзадача — такая же полноценная задача (свои имя, ключ, интервал, лаги), но запущенная другой задачей. В `/pn tasks` и меню она показана внутри родителя.

```kotlin
run { ctx ->
    ctx.spawn {                                  // билдер: любые настройки
        name = "Таймер"
        interval = Duration.ofSeconds(1)
        cancelWithParent = true                  // отменится вместе с родителем
        run { sub -> tick(sub.runNumber) }
    }
    ctx.spawn(Autosave("nether"))                // класс или готовая спека
    ctx.later(Duration.ofSeconds(5)) { announce("Старт") }          // один раз позже
    ctx.repeat(5, Duration.ofSeconds(10)) { sub -> countdown() }    // N раз
}
```

`spawn` работает и внутри подзадачи, глубина любая. Родитель виден как `TaskContext.parentId` и `TaskSnapshot.parentId`.

## Лаги сервера

Если сервер завис, запуск начнётся позже плана. Узнать об этом можно тремя способами:

| Способ | Что даёт |
|---|---|
| `ctx.lag`, `ctx.missedRuns`, `ctx.isLate(порог)` | проверка прямо внутри задачи |
| `onLag(порог) { lag -> LagResponse }` / `override fun onLag` | решить: `RUN` (выполнить), `SKIP` (пропустить этот запуск) или `CANCEL` (отменить задачу насовсем) |
| `whenLate(порог) { lag -> }` | только узнать, запуск продолжится |

`maxLag` — предел: запуск, запоздавший дольше, пропускается без вызова действия. `tolerateFailures` — сколько ошибок подряд повторяющаяся задача переживёт. В снимке задачи: `lastLag`, `worstLag`, `failedCount`, `skippedCount`; администратор видит их в иконке «Опоздания».
