package ru.privatenull.pnlibrary.api.actions

import java.util.UUID

/** Supported platform-independent actions loaded directly from YAML. */
enum class PlayerActionType { MESSAGE, TITLE, ACTION_BAR, KICK, TELEPORT, SOUND, PLAYER_COMMAND }

/** One configuration-friendly action. Only fields relevant to [type] are used. */
class PlayerAction @JvmOverloads constructor(
    var type: PlayerActionType = PlayerActionType.MESSAGE,
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
)

/** Ordered action scenario represented in YAML as an object containing `actions`. */
class PlayerActionSequence @JvmOverloads constructor(
    var actions: MutableList<PlayerAction> = mutableListOf(),
)

/** Executes configured actions for a player identified without native platform classes. */
interface PlayerActionService {
    fun execute(playerId: UUID, sequence: PlayerActionSequence)
    fun execute(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?>)
}
