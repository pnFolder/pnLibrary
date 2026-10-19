package org.pnfolder.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Isolates Folia scheduler access from the legacy Bukkit classpath.
 *
 * Reflection avoids linking optional Folia classes on servers that do not provide them.
 * Methods are resolved with their declared parameter types, including primitive durations.
 */
internal class FoliaSchedulerBridge(
    private val plugin: Plugin,
) {
    /** Routes a request to the asynchronous, player, or global-region scheduler. */
    fun schedule(request: PlatformTaskRequest): Any {
        return when (request.executionKind) {
            TaskExecution.Kind.ASYNC -> scheduleFoliaAsync(request)
            TaskExecution.Kind.ENTITY -> scheduleFoliaEntity(request)
            TaskExecution.Kind.GLOBAL -> scheduleFoliaGlobal(request)
        }
    }

    /** Schedules asynchronous work using nanosecond delays and intervals. */
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

    /** Schedules work on the target player's owning region. */
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

    /** Schedules global-region work using server ticks. */
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

    /** Unwraps command senders and validates the native player target. */
    private fun resolvePlayer(target: Any?): Player {
        val native = (target as? BukkitCommandSender)?.native ?: target
        return requireNotNull(native as? Player) {
            "Folia entity task requires a Bukkit Player target"
        }
    }

    /** Adapts the portable callback to Folia's consumer contract. */
    private fun callback(request: PlatformTaskRequest): Consumer<Any> =
        Consumer { request.callback.run() }

    /** Resolves an optional static scheduler accessor without linking Folia classes. */
    private fun bukkitMethod(name: String) =
        Bukkit::class.java.getMethod(name)

    /** Invokes a native method using the declared types of the supplied arguments. */
    private fun invoke(receiver: Any, name: String, vararg args: Any?): Any =
        receiver.javaClass.getMethod(name, *args.map(::parameterType).toTypedArray()).invoke(receiver, *args)

    /** Resolves interface and primitive parameter types used by scheduler overloads. */
    private fun parameterType(argument: Any?): Class<*> = when (argument) {
        null -> Runnable::class.java
        is Plugin -> Plugin::class.java
        is Consumer<*> -> Consumer::class.java
        is Long -> java.lang.Long.TYPE
        is TimeUnit -> TimeUnit::class.java
        else -> argument.javaClass
    }

    /** Cancels a native Folia task through its public scheduler contract. */
    fun cancel(task: Any) {
        Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
            .getMethod("cancel").invoke(task)
    }
}
