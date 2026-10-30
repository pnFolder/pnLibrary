package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.ViewAction
import org.pnfolder.pnlibrary.core.link.ViewFrame
import org.pnfolder.pnlibrary.core.link.ViewHandler
import org.pnfolder.pnlibrary.core.link.ViewNotices
import org.pnfolder.pnlibrary.core.link.ViewRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Turns an [ItemSpec] into bytes for the link and back. */
object ItemSpecs {
    /** [spec] as bytes. */
    fun encode(spec: ItemSpec): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(spec.texture.orEmpty())
            out.writeUTF(spec.material)
            out.writeUTF(spec.alternative.orEmpty())
            out.writeUTF(spec.name)
            out.writeShort(spec.legacyData.toInt())
            out.writeInt(spec.lore.size)
            spec.lore.forEach(out::writeUTF)
        }
        return bytes.toByteArray()
    }

    /** The item in [bytes], or null when they do not hold one. */
    fun decode(bytes: ByteArray): ItemSpec? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val texture = input.readUTF().ifEmpty { null }
            val material = input.readUTF()
            val alternative = input.readUTF().ifEmpty { null }
            val name = input.readUTF()
            val legacy = input.readShort()
            ItemSpec(texture, material, alternative, name, List(input.readInt().coerceIn(0, MAX_LORE)) { input.readUTF() }, legacy)
        }
    }.getOrNull()

    private const val MAX_LORE = 80
}

/**
 * Draws the menu for viewers on other servers and carries out their clicks.
 *
 * It runs on the server that owns the menu, a game server or a proxy. A viewer's step arrives as a [ViewRequest]; the
 * page that results goes back as a [ViewFrame] through [send], which the platform ties to the link.
 *
 * @param key the shared link key
 * @param home opens the home page for a viewer
 * @param send sends signed bytes to the server called by the second argument; false when that is not possible
 * @param run runs a command for a viewer and returns what it printed
 * @param rewrite changes a chat message before the viewer sees it, for text that names this server's own commands
 * @param warn reports a failure of a click
 */
class RemoteMenuHost(
    private val key: ByteArray,
    private val home: (MenuViewer) -> Unit,
    private val send: (ByteArray, String) -> Boolean,
    private val run: (MenuViewer, String) -> List<Component>,
    private val rewrite: (Component) -> Component = { it },
    private val warn: (String) -> Unit = {},
) : ViewHandler {
    private val sessions = ConcurrentHashMap<UUID, Viewer>()

    /** Handles a viewer's step; call it on the thread that may touch the platform. */
    override fun handle(request: ViewRequest) {
        purge()
        when (request.action) {
            ViewAction.OPEN -> open(request)
            ViewAction.CLICK -> sessions[request.session]?.let { click(it, request) }
                ?: finish(request.session, request.origin, "Сеанс меню закончился. Откройте его снова.")
            ViewAction.CLOSE -> sessions.remove(request.session)
        }
    }

    /** Stops every session. */
    fun stop() = sessions.clear()

    private fun open(request: ViewRequest) {
        if ("pnlibrary.admin" !in request.permissions && "pnlibrary.servers" !in request.permissions) {
            return finish(request.session, request.origin, "Недостаточно прав для меню этого сервера.")
        }
        val viewer = Viewer(request.session, request.viewer, request.origin, request.permissions)
        sessions[request.session] = viewer
        try {
            home(viewer)
        } catch (error: Exception) {
            finish(request.session, request.origin, "Не удалось открыть меню: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun click(viewer: Viewer, request: ViewRequest) {
        viewer.touched = Instant.now()
        val handler = viewer.page?.items?.get(request.slot)?.handler ?: return
        val type = ClickType.values().firstOrNull { it.name == request.click } ?: ClickType.LEFT
        try {
            handler.handle(MenuClick(viewer, request.slot, type))
        } catch (error: Exception) {
            warn("Remote menu click failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun frame(viewer: Viewer, page: Menu?, notices: List<Component>, closed: Boolean) {
        val items = page?.items.orEmpty().mapNotNull { (slot, entry) -> entry.item?.let { slot to ItemSpecs.encode(it) } }.toMap()
        val frame = ViewFrame(
            viewer.id, viewer.origin, page?.title.orEmpty(), page?.rows ?: 0, items, notices.map { ViewNotices.encode(rewrite(it)) }, closed, Instant.now(),
        )
        val message = LinkCodec.encodeFrame(frame, key)
        if (message == null) {
            finish(viewer.id, viewer.origin, "Страница меню слишком большая для передачи.")
        } else {
            send(message, viewer.origin)
        }
    }

    private fun finish(session: UUID, origin: String, text: String) {
        val frame = ViewFrame(session, origin, "", 0, emptyMap(), listOf(ViewNotices.encode(Component.text(text))), true, Instant.now())
        LinkCodec.encodeFrame(frame, key)?.let { send(it, origin) }
    }

    private fun purge() {
        val limit = Instant.now().minus(IDLE)
        sessions.values.removeIf { it.touched.isBefore(limit) }
    }

    private inner class Viewer(
        override val id: UUID,
        override val name: String,
        val origin: String,
        private val permissions: Set<String>,
    ) : MenuViewer {
        @Volatile
        var page: Menu? = null

        @Volatile
        var touched: Instant = Instant.now()

        override val online: Boolean get() = sessions.containsKey(id)
        override val remote: Boolean get() = true
        override val actor: UpdateActor = UpdateActor(UpdateActor.Kind.INTEGRATION, "remote:$origin", "$name ($origin)")

        override fun hasPermission(permission: String): Boolean =
            permission in permissions || "pnlibrary.admin" in permissions && permission.startsWith("pnlibrary.")

        override fun open(menu: Menu) {
            page = menu
            touched = Instant.now()
            frame(this, menu, emptyList(), false)
        }

        override fun close() = frame(this, null, emptyList(), true)

        override fun send(lines: List<Component>) {
            if (lines.isNotEmpty()) frame(this, null, lines, false)
        }

        override fun runCommand(command: String) {
            val lines = try {
                run(this, command)
            } catch (error: Exception) {
                listOf(Component.text("Команда не выполнилась: /$command"))
            }
            send(lines)
        }
    }

    private companion object {
        val IDLE: Duration = Duration.ofMinutes(10)
    }
}
