package org.pnfolder.pnlibrary.bukkit.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Folia's region schedulers as plain functions.
 *
 * <p>The Bukkit runtime compiles against Spigot 1.8, which has none of these types, so every method is looked up
 * once, when this class is first used. Touch it only when {@link ServerCapabilities#isFolia()} is true.
 *
 * <p>Delays and periods of global and entity tasks are in server ticks; asynchronous tasks use real time.
 */
public final class Folia {
    private static final String SCHEDULERS = "io.papermc.paper.threadedregions.scheduler.";
    private static final Class<?> LONG = Long.TYPE;

    private static final Object ASYNC_SCHEDULER;
    private static final Object GLOBAL_SCHEDULER;
    private static final Method ENTITY_SCHEDULER;
    private static final Method OWNED_BY_CURRENT_REGION;
    private static final Method ASYNC_NOW;
    private static final Method ASYNC_DELAYED;
    private static final Method ASYNC_REPEATING;
    private static final Method GLOBAL_NOW;
    private static final Method GLOBAL_DELAYED;
    private static final Method GLOBAL_REPEATING;
    private static final Method ENTITY_NOW;
    private static final Method ENTITY_DELAYED;
    private static final Method ENTITY_REPEATING;
    private static final Method CANCEL_TASK;

    static {
        try {
            Class<?> asyncType = Class.forName(SCHEDULERS + "AsyncScheduler");
            Class<?> globalType = Class.forName(SCHEDULERS + "GlobalRegionScheduler");
            Class<?> entityType = Class.forName(SCHEDULERS + "EntityScheduler");
            Class<?> taskType = Class.forName(SCHEDULERS + "ScheduledTask");
            ASYNC_SCHEDULER = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            GLOBAL_SCHEDULER = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            ENTITY_SCHEDULER = Entity.class.getMethod("getScheduler");
            OWNED_BY_CURRENT_REGION = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            ASYNC_NOW = asyncType.getMethod("runNow", Plugin.class, Consumer.class);
            ASYNC_DELAYED = asyncType.getMethod("runDelayed", Plugin.class, Consumer.class, LONG, TimeUnit.class);
            ASYNC_REPEATING = asyncType.getMethod("runAtFixedRate", Plugin.class, Consumer.class, LONG, LONG, TimeUnit.class);
            GLOBAL_NOW = globalType.getMethod("run", Plugin.class, Consumer.class);
            GLOBAL_DELAYED = globalType.getMethod("runDelayed", Plugin.class, Consumer.class, LONG);
            GLOBAL_REPEATING = globalType.getMethod("runAtFixedRate", Plugin.class, Consumer.class, LONG, LONG);
            ENTITY_NOW = entityType.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            ENTITY_DELAYED = entityType.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, LONG);
            ENTITY_REPEATING = entityType.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, LONG, LONG);
            CANCEL_TASK = taskType.getMethod("cancel");
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private Folia() {
    }

    public static boolean ownsRegionOf(Entity entity) {
        return Boolean.TRUE.equals(call(OWNED_BY_CURRENT_REGION, null, entity));
    }

    public static Object runAsync(Plugin plugin, Runnable task) {
        return call(ASYNC_NOW, ASYNC_SCHEDULER, plugin, consumer(task));
    }

    public static Object runAsyncLater(Plugin plugin, Duration delay, Runnable task) {
        return call(ASYNC_DELAYED, ASYNC_SCHEDULER, plugin, consumer(task), delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    public static Object runAsyncRepeating(Plugin plugin, Duration delay, Duration period, Runnable task) {
        return call(ASYNC_REPEATING, ASYNC_SCHEDULER, plugin, consumer(task), delay.toNanos(), period.toNanos(),
            TimeUnit.NANOSECONDS);
    }

    public static Object runGlobal(Plugin plugin, Runnable task) {
        return call(GLOBAL_NOW, GLOBAL_SCHEDULER, plugin, consumer(task));
    }

    public static Object runGlobalLater(Plugin plugin, long delayTicks, Runnable task) {
        return call(GLOBAL_DELAYED, GLOBAL_SCHEDULER, plugin, consumer(task), delayTicks);
    }

    public static Object runGlobalRepeating(Plugin plugin, long delayTicks, long periodTicks, Runnable task) {
        return call(GLOBAL_REPEATING, GLOBAL_SCHEDULER, plugin, consumer(task), delayTicks, periodTicks);
    }

    /** Runs {@code task} on the region of {@code entity}; {@code null} is returned when it is already gone. */
    public static Object runOn(Entity entity, Plugin plugin, Runnable task) {
        return runOn(entity, plugin, task, null);
    }

    /** Runs {@code task} on the region of {@code entity}; {@code retired} runs if the entity goes away first. */
    public static Object runOn(Entity entity, Plugin plugin, Runnable task, Runnable retired) {
        return call(ENTITY_NOW, scheduler(entity), plugin, consumer(task), retired);
    }

    public static Object runOnLater(Entity entity, Plugin plugin, long delayTicks, Runnable task) {
        return runOnLater(entity, plugin, delayTicks, task, null);
    }

    public static Object runOnLater(Entity entity, Plugin plugin, long delayTicks, Runnable task, Runnable retired) {
        return call(ENTITY_DELAYED, scheduler(entity), plugin, consumer(task), retired, delayTicks);
    }

    public static Object runOnRepeating(Entity entity, Plugin plugin, long delayTicks, long periodTicks, Runnable task,
                                        Runnable retired) {
        return call(ENTITY_REPEATING, scheduler(entity), plugin, consumer(task), retired, delayTicks, periodTicks);
    }

    public static void cancel(Object task) {
        call(CANCEL_TASK, task);
    }

    private static Object scheduler(Entity entity) {
        Object scheduler = call(ENTITY_SCHEDULER, entity);
        if (scheduler == null) {
            throw new IllegalStateException("Entity scheduler is unavailable");
        }
        return scheduler;
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    // The failure of the scheduler itself matters, not the reflective wrapper around it.
    private static Object call(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }
}
