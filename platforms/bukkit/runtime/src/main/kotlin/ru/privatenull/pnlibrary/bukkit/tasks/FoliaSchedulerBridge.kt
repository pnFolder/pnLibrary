package ru.privatenull.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandSender
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/** Isolates optional Folia scheduler access and resolves methods by their parameter types. */
internal class FoliaSchedulerBridge(private val plugin: Plugin) {
    fun schedule(request: PlatformTaskRequest): Any {
        return when (request.executionKind) {
            TaskExecution.Kind.ASYNC -> scheduleFoliaAsync(request)
            TaskExecution.Kind.ENTITY -> scheduleFoliaEntity(request)
            TaskExecution.Kind.GLOBAL -> scheduleFoliaGlobal(request)
        }
    }

    private fun scheduleFoliaAsync(request: PlatformTaskRequest): Any {
        val scheduler = bukkitMethod("getAsyncScheduler").invoke(null)
        val callback = callback(request)
        val interval = request.interval

        return when {
            interval != null -> invoke(scheduler, "runAtFixedRate", plugin, callback,
                request.delay.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS)
            request.delay.isZero -> invoke(scheduler, "runNow", plugin, callback)
            else -> invoke(scheduler, "runDelayed", plugin, callback,
                request.delay.toNanos(), TimeUnit.NANOSECONDS)
        }
    }

    private fun scheduleFoliaEntity(request: PlatformTaskRequest): Any {
        val player = resolvePlayer(request.target)
        val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
        val callback = callback(request)

        val delay = durationToTicks(request.delay).coerceAtLeast(1)
        val period = request.interval?.let(::durationToTicks)

        return when {
            period != null -> invoke(scheduler, "runAtFixedRate", plugin, callback, null, delay, period)
            request.delay.isZero -> invoke(scheduler, "run", plugin, callback, null)
            else -> invoke(scheduler, "runDelayed", plugin, callback, null, delay)
        }
    }

    private fun scheduleFoliaGlobal(request: PlatformTaskRequest): Any {
        val scheduler = bukkitMethod("getGlobalRegionScheduler").invoke(null)
        val callback = callback(request)
        val delay = durationToTicks(request.delay)
        val period = request.interval?.let(::durationToTicks)

        return when {
            period != null -> invoke(scheduler, "runAtFixedRate", plugin, callback, delay, period)
            request.delay.isZero -> invoke(scheduler, "run", plugin, callback)
            else -> invoke(scheduler, "runDelayed", plugin, callback, delay)
        }
    }

    private fun resolvePlayer(target: Any?): Player {
        val native = (target as? BukkitCommandSender)?.native ?: target
        return requireNotNull(native as? Player) {
            "Folia entity task requires a Bukkit Player target"
        }
    }

    private fun callback(request: PlatformTaskRequest): Consumer<Any> =
        Consumer { request.callback.run() }

    private fun bukkitMethod(name: String) =
        Bukkit::class.java.getMethod(name)

    private fun invoke(receiver: Any, name: String, vararg args: Any?): Any =
        receiver.javaClass.getMethod(name, *args.map(::parameterType).toTypedArray()).invoke(receiver, *args)

    private fun parameterType(argument: Any?): Class<*> = when (argument) {
        null -> Runnable::class.java
        is Plugin -> Plugin::class.java
        is Consumer<*> -> Consumer::class.java
        is Long -> java.lang.Long.TYPE
        is TimeUnit -> TimeUnit::class.java
        else -> argument.javaClass
    }

    fun cancel(task: Any) {
        Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
            .getMethod("cancel").invoke(task)
    }
}
