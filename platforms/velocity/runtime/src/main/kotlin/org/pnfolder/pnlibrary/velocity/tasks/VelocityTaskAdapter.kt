package org.pnfolder.pnlibrary.velocity.tasks

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter

internal class VelocityTaskAdapter internal constructor(
    private val warning: (String) -> Unit,
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : TrackingPlatformTaskAdapter() {

    constructor(server: ProxyServer, plugin: Any, warning: (String) -> Unit) : this(
        warning = warning,
        nativeSchedule = { request ->
            require(server.pluginManager.fromInstance(plugin).isPresent) {
                "Task owner must be a registered Velocity plugin"
            }
            val builder = server.scheduler.buildTask(plugin, request.callback)
            if (!request.delay.isZero) builder.delay(request.delay)
            request.interval?.let(builder::repeat)

            val task = builder.schedule()
            PlatformTaskHandle { task.cancel(); true }
        },
    )

    override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle = nativeSchedule(request)

    // Velocity has no entity scheduler; entity tasks fall back to the proxy scheduler.
    override fun beforeSchedule(request: PlatformTaskRequest) {
        if (request.executionKind == TaskExecution.Kind.ENTITY) {
            warning("Entity-bound task requested on Velocity, which has no entity scheduler; running on the proxy scheduler instead.")
        }
    }
}
