package org.pnfolder.pnlibrary.velocity.tasks

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.spi.tasks.TrackingPlatformTaskAdapter

/**
 * Adapts pnLibrary task requests to the native Velocity scheduler.
 *
 * Handle tracking, one-shot release and shutdown come from [TrackingPlatformTaskAdapter].
 */
internal class VelocityTaskAdapter internal constructor(
    private val warning: (String) -> Unit,
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : TrackingPlatformTaskAdapter() {

    /** Adapts [plugin] (a registered Velocity plugin) to the native scheduler; entity tasks fall back to the proxy scheduler with a [warning]. */
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

    /** Velocity has no entity scheduler, so entity tasks run on the proxy scheduler and emit a [warning]. */
    override fun beforeSchedule(request: PlatformTaskRequest) {
        if (request.executionKind == TaskExecution.Kind.ENTITY) {
            warning("Запрошена задача, привязанная к игроку. Velocity такие задачи не поддерживает: задача выполнена на общем планировщике прокси.")
        }
    }
}
