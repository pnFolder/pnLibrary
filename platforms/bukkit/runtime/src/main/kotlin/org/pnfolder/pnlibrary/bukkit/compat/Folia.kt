package org.pnfolder.pnlibrary.bukkit.compat

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Folia's region schedulers as plain functions.
 *
 * The Bukkit runtime compiles against Spigot 1.8, which has none of these types, so every method is looked up once,
 * when this object is first used. Touch it only when [ServerCapabilities.isFolia] is true.
 *
 * Delays and periods of global and entity tasks are in server ticks; asynchronous tasks use real time.
 */
internal object Folia {
    private const val SCHEDULERS = "io.papermc.paper.threadedregions.scheduler."
    private val LONG = java.lang.Long.TYPE

    private val asyncType = Class.forName(SCHEDULERS + "AsyncScheduler")
    private val globalType = Class.forName(SCHEDULERS + "GlobalRegionScheduler")
    private val entityType = Class.forName(SCHEDULERS + "EntityScheduler")
    private val taskType = Class.forName(SCHEDULERS + "ScheduledTask")

    private val asyncScheduler: Any = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
    private val globalScheduler: Any = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
    private val entityScheduler = Entity::class.java.getMethod("getScheduler")
    private val ownedByCurrentRegion = Bukkit::class.java.getMethod("isOwnedByCurrentRegion", Entity::class.java)

    private val asyncNow = asyncType.getMethod("runNow", Plugin::class.java, Consumer::class.java)
    private val asyncDelayed = asyncType.getMethod("runDelayed", Plugin::class.java, Consumer::class.java, LONG, TimeUnit::class.java)
    private val asyncRepeating =
        asyncType.getMethod("runAtFixedRate", Plugin::class.java, Consumer::class.java, LONG, LONG, TimeUnit::class.java)
    private val globalNow = globalType.getMethod("run", Plugin::class.java, Consumer::class.java)
    private val globalDelayed = globalType.getMethod("runDelayed", Plugin::class.java, Consumer::class.java, LONG)
    private val globalRepeating = globalType.getMethod("runAtFixedRate", Plugin::class.java, Consumer::class.java, LONG, LONG)
    private val entityNow = entityType.getMethod("run", Plugin::class.java, Consumer::class.java, Runnable::class.java)
    private val entityDelayed = entityType.getMethod("runDelayed", Plugin::class.java, Consumer::class.java, Runnable::class.java, LONG)
    private val entityRepeating =
        entityType.getMethod("runAtFixedRate", Plugin::class.java, Consumer::class.java, Runnable::class.java, LONG, LONG)
    private val cancelTask = taskType.getMethod("cancel")

    fun ownsRegionOf(entity: Entity): Boolean = ownedByCurrentRegion.call(null, entity) == true

    fun runAsync(plugin: Plugin, task: Runnable): Any? = asyncNow.call(asyncScheduler, plugin, consumer(task))

    fun runAsyncLater(plugin: Plugin, delay: Duration, task: Runnable): Any? =
        asyncDelayed.call(asyncScheduler, plugin, consumer(task), delay.toNanos(), TimeUnit.NANOSECONDS)

    fun runAsyncRepeating(plugin: Plugin, delay: Duration, period: Duration, task: Runnable): Any? =
        asyncRepeating.call(asyncScheduler, plugin, consumer(task), delay.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS)

    fun runGlobal(plugin: Plugin, task: Runnable): Any? = globalNow.call(globalScheduler, plugin, consumer(task))

    fun runGlobalLater(plugin: Plugin, delayTicks: Long, task: Runnable): Any? =
        globalDelayed.call(globalScheduler, plugin, consumer(task), delayTicks)

    fun runGlobalRepeating(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: Runnable): Any? =
        globalRepeating.call(globalScheduler, plugin, consumer(task), delayTicks, periodTicks)

    /** Runs [task] on the region of [entity]; [retired] runs if the entity goes away first. Returns `null` when it is already gone. */
    fun runOn(entity: Entity, plugin: Plugin, task: Runnable, retired: Runnable? = null): Any? =
        entityNow.call(scheduler(entity), plugin, consumer(task), retired)

    fun runOnLater(entity: Entity, plugin: Plugin, delayTicks: Long, task: Runnable, retired: Runnable? = null): Any? =
        entityDelayed.call(scheduler(entity), plugin, consumer(task), retired, delayTicks)

    fun runOnRepeating(entity: Entity, plugin: Plugin, delayTicks: Long, periodTicks: Long, task: Runnable, retired: Runnable? = null): Any? =
        entityRepeating.call(scheduler(entity), plugin, consumer(task), retired, delayTicks, periodTicks)

    fun cancel(task: Any) {
        cancelTask.call(task)
    }

    private fun scheduler(entity: Entity): Any = checkNotNull(entityScheduler.call(entity))

    private fun consumer(task: Runnable) = Consumer<Any> { task.run() }

    // The failure of the scheduler itself matters, not the reflective wrapper around it.
    private fun Method.call(receiver: Any?, vararg args: Any?): Any? = try {
        invoke(receiver, *args)
    } catch (error: InvocationTargetException) {
        throw error.cause ?: error
    }
}
