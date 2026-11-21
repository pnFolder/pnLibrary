package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot

// Owner identity (not equals) is used for scope lookup — distinct but equal objects get distinct scopes.
internal interface TaskService : AutoCloseable {
    fun scope(owner: Any): TaskScope
    fun scope(owner: Any, scopeId: PluginId): TaskScope = scope(owner)
    fun get(id: TaskId): TaskHandle? = null
    fun require(id: TaskId): TaskHandle = get(id) ?: error("Task $id is unavailable")
    fun query(query: TaskQuery = TaskQuery.all()): List<TaskSnapshot> = emptyList()
    fun query(owner: Any, query: TaskQuery = TaskQuery.all()): List<TaskSnapshot> = emptyList()
    fun cancel(id: TaskId): Boolean = get(id)?.cancelIfActive() ?: false
    fun cancelByAdmin(id: TaskId): TaskControl = TaskControl.UNAVAILABLE
    fun runNow(id: TaskId): TaskControl = TaskControl.UNAVAILABLE
    fun close(owner: Any)

    override fun close()
}
