package ru.privatenull.pnlibrary.api.actions

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Supported platform-independent actions loaded directly from YAML. */
enum class PlayerActionType { MESSAGE, TITLE, ACTION_BAR, KICK, TELEPORT, SOUND, PLAYER_COMMAND }

/** One configuration-friendly action. Only fields relevant to [type] are used. */
class PlayerAction @JvmOverloads constructor(
    /** Handler key: a built-in name such as `message`, or a custom namespaced key. */
    var type: String = "message",
    var text: String? = null,
    var title: String? = null,
    var subtitle: String? = null,
    var fadeIn: Int = 10,
    var stay: Int = 70,
    var fadeOut: Int = 20,
    var world: String? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var sound: String? = null,
    var volume: Float = 1f,
    var soundPitch: Float = 1f,
    var command: String? = null,
    /** Arbitrary configuration values consumed by custom handlers. */
    var arguments: MutableMap<String, Any?> = linkedMapOf(),
    /** Optional object passed to a handler by code; it is not required to come from YAML. */
    @field:ru.privatenull.pnlibrary.api.config.ConfigIgnore var payload: Any? = null,
)

/** Ordered action scenario represented in YAML as an object containing `actions`. */
class PlayerActionSequence @JvmOverloads constructor(
    var actions: MutableList<PlayerAction> = mutableListOf(),
)

/** Executes configured actions for a player identified without native platform classes. */
interface PlayerActionService {
    fun execute(playerId: UUID, sequence: PlayerActionSequence)
    fun execute(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?>)
    fun executeAsync(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?> = emptyMap()): CompletionStage<ActionSequenceResult>
    fun register(handler: String, actionHandler: PlayerActionHandler): PlayerActionRegistration
}

data class PlayerActionContext(
    val owner: PluginId,
    val playerId: UUID,
    val handler: String,
    val action: PlayerAction,
    val message: Component?,
    val title: Component?,
    val subtitle: Component?,
    val arguments: Map<String, Any?>,
    val payload: Any?,
) {
    fun require(name: String): Any = arguments[name] ?: error("Action $handler requires argument '$name'")
    fun requireString(name: String): String = require(name).toString()
    fun requireInt(name: String): Int = (require(name) as? Number)?.toInt()
        ?: require(name).toString().toIntOrNull() ?: error("Action argument '$name' must be an integer")
    fun requireDouble(name: String): Double = (require(name) as? Number)?.toDouble()
        ?: require(name).toString().toDoubleOrNull() ?: error("Action argument '$name' must be a number")
}

data class PlayerActionResult(val successful: Boolean, val message: String? = null) {
    companion object {
        @JvmStatic fun success() = PlayerActionResult(true)
        @JvmStatic fun skipped(message: String) = PlayerActionResult(false, message)
    }
}

data class ActionSequenceResult(val actions: List<PlayerActionResult>) {
    val successful: Boolean get() = actions.all(PlayerActionResult::successful)
}

fun interface PlayerActionHandler {
    fun execute(context: PlayerActionContext): CompletionStage<PlayerActionResult>

    companion object {
        @JvmStatic fun immediate(handler: java.util.function.Function<PlayerActionContext, PlayerActionResult>) =
            PlayerActionHandler { CompletableFuture.completedFuture(handler.apply(it)) }
    }
}

interface PlayerActionRegistration : AutoCloseable {
    val handler: String
    val isActive: Boolean
}
