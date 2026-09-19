package ru.privatenull.pnlibrary.bukkit.tasks

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandSender
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.spi.tasks.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.ceil

internal fun durationToTicks(value: Duration): Long =
    if (value.isZero) 0 else ceil(value.toNanos() / 50_000_000.0).toLong().coerceAtLeast(1)

internal class BukkitTaskAdapter(private val plugin: Plugin) : PlatformTaskAdapter {
    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap.newKeySet<NativeHandle>()

    override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
        check(!closed.get()) { "Bukkit task adapter is closed" }
        val native = if (ServerCapabilities.isFolia) scheduleFolia(request) else scheduleBukkit(request)
        return NativeHandle(native).also { handles += it }
    }

    private fun scheduleBukkit(request: PlatformTaskRequest): Any {
        val scheduler = Bukkit.getScheduler()
        val delay = durationToTicks(request.delay)
        val period = request.interval?.let(::durationToTicks)
        val async = request.executionKind == TaskExecution.Kind.ASYNC
        return when {
            period != null && async -> scheduler.runTaskTimerAsynchronously(plugin, request.callback, delay, period)
            period != null -> scheduler.runTaskTimer(plugin, request.callback, delay, period)
            delay > 0 && async -> scheduler.runTaskLaterAsynchronously(plugin, request.callback, delay)
            delay > 0 -> scheduler.runTaskLater(plugin, request.callback, delay)
            async -> scheduler.runTaskAsynchronously(plugin, request.callback)
            else -> scheduler.runTask(plugin, request.callback)
        }
    }

    private fun scheduleFolia(request: PlatformTaskRequest): Any {
        if (request.executionKind == TaskExecution.Kind.ASYNC) {
            val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
            val consumer = Consumer<Any> { request.callback.run() }
            val interval = request.interval
            return when {
                interval != null -> invoke(scheduler, "runAtFixedRate", plugin, consumer,
                    request.delay.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS)
                !request.delay.isZero -> invoke(scheduler, "runDelayed", plugin, consumer,
                    request.delay.toNanos(), TimeUnit.NANOSECONDS)
                else -> invoke(scheduler, "runNow", plugin, consumer)
            }
        }
        val target = (request.target as? BukkitCommandSender)?.native ?: request.target
        val entity = request.executionKind == TaskExecution.Kind.ENTITY
        require(!entity || target is Player) { "Folia entity task requires a Bukkit Player target" }
        val scheduler = if (entity) target!!.javaClass.getMethod("getScheduler").invoke(target)
            else Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val consumer = Consumer<Any> { request.callback.run() }
        val delay = durationToTicks(request.delay).coerceAtLeast(1)
        val period = request.interval?.let(::durationToTicks)
        return if (entity) when {
            period != null -> invoke(scheduler, "runAtFixedRate", plugin, consumer, null, delay, period)
            request.delay.isZero -> invoke(scheduler, "run", plugin, consumer, null)
            else -> invoke(scheduler, "runDelayed", plugin, consumer, null, delay)
        } else when {
            period != null -> invoke(scheduler, "runAtFixedRate", plugin, consumer, delay, period)
            request.delay.isZero -> invoke(scheduler, "run", plugin, consumer)
            else -> invoke(scheduler, "runDelayed", plugin, consumer, delay)
        }
    }

    private fun invoke(receiver: Any, name: String, vararg args: Any?): Any =
        receiver.javaClass.methods.first { it.name == name && it.parameterCount == args.size }.invoke(receiver, *args)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handles.toList().forEach { it.cancel() }
        handles.clear()
    }

    private inner class NativeHandle(private val native: Any) : PlatformTaskHandle {
        private val cancelled = AtomicBoolean(false)
        override fun cancel(): Boolean {
            if (!cancelled.compareAndSet(false, true)) return false
            when (native) {
                is BukkitTask -> native.cancel()
                else -> native.javaClass.getMethod("cancel").invoke(native)
            }
            handles.remove(this)
            return true
        }
    }
}
