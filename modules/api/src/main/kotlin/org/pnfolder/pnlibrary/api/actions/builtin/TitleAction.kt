package org.pnfolder.pnlibrary.api.actions.builtin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType
import java.time.Duration

/**
 * Shows a large title and a smaller subtitle in the middle of the screen.
 *
 * Platforms that cannot show titles (the proxies) write a warning to the action logger and execution
 * continues.
 *
 * @property title main line; may be empty
 * @property subtitle second line; may be empty
 * @property fadeIn how long the title takes to appear
 * @property stay how long it stays fully visible
 * @property fadeOut how long it takes to disappear
 * @property serializerType parser override, or `null` to inherit [ActionContext.serializerType]
 * @property target audience that sees the title
 */
data class TitleAction(
    val title: String = "",
    val subtitle: String = "",
    val fadeIn: Duration = Duration.ofMillis(500),
    val stay: Duration = Duration.ofMillis(3500),
    val fadeOut: Duration = Duration.ofMillis(1000),
    val serializerType: ComponentSerializerType? = null,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    init {
        require(!fadeIn.isNegative && !stay.isNegative && !fadeOut.isNegative) { "Title times must not be negative" }
    }

    override fun execute(context: ActionContext) {
        val shown = context.target(target).showTitle(
            context.component(title, serializerType),
            context.component(subtitle, serializerType),
            fadeIn,
            stay,
            fadeOut,
        )
        if (!shown) context.logger.warning("Title action is unsupported by the current platform")
    }
}
