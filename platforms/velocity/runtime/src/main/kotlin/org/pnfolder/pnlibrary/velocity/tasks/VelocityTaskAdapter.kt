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
    private val describeOwner: (Any?) -> String,
    private val warning: (String) -> Unit,
    private val nativeSchedule: (PlatformTaskRequest) -> PlatformTaskHandle,
) : TrackingPlatformTaskAdapter() {

    /**
     * Creates an adapter backed by Velocity's scheduler. [describeOwner] names the plugin that asked for a task,
     * and [warning] receives a notice every time an entity task is run on the proxy scheduler instead.
     */
    constructor(server: ProxyServer, describeOwner: (Any?) -> String, warning: (String) -> Unit) : this(
        describeOwner = describeOwner,
        warning = warning,
        nativeSchedule = { request ->
            val owner = requireNotNull(request.owner) { "Velocity tasks require a native plugin owner" }
            require(server.pluginManager.fromInstance(owner).isPresent) {
                "Task owner must be a registered Velocity plugin"
            }
            val builder = server.scheduler.buildTask(owner, request.callback)

            if (!request.delay.isZero) {
                builder.delay(request.delay)
            }
            request.interval?.let(builder::repeat)

            val task = builder.schedule()
            PlatformTaskHandle {
                task.cancel()
                true
            }
        },
    )

    override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle = nativeSchedule(request)

    /**
     * Velocity has no entity scheduler, so an entity task runs on the proxy scheduler instead, and every such
     * call writes a warning that names the plugin which asked for it.
     */
    override fun beforeSchedule(request: PlatformTaskRequest) {
        if (request.executionKind == TaskExecution.Kind.ENTITY) {
            warning(
                "Плагин ${describeOwner(request.owner)} запросил задачу, привязанную к игроку. " +
                    "Velocity такие задачи не поддерживает: задача выполнена на общем планировщике прокси.",
            )
        }
    }
}
