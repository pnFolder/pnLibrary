package org.pnfolder.pnlibrary.bungee.tasks

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter
import java.util.concurrent.TimeUnit

/**
 * Adapts portable task requests to BungeeCord's asynchronous scheduler.
 *
 * BungeeCord has no main-thread or entity-region scheduler. Every execution kind uses the
 * same native scheduler; completed one-shot tasks are released from lifecycle tracking.
 */
internal class BungeeTaskAdapter internal constructor(
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : TrackingPlatformTaskAdapter() {
    /** Creates an adapter backed by the owning plugin's scheduler. */
    constructor(plugin: Plugin) : this({ request ->
        val scheduler = plugin.proxy.scheduler
        val interval = request.interval
        val task = if (interval == null) {
            scheduler.schedule(
                plugin,
                request.callback,
                request.delay.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        } else {
            scheduler.schedule(
                plugin,
                request.callback,
                request.delay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        }

        PlatformTaskHandle {
            task.cancel()
            true
        }
    })


    override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle = nativeSchedule(request)
}
