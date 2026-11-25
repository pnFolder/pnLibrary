package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import net.kyori.adventure.title.Title
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType
import java.time.Duration

/**
 * Shows a large title with a smaller subtitle. Platforms that cannot show titles show nothing.
 *
 * @property title main line
 * @property subtitle second line
 * @property fadeIn time to appear
 * @property stay time fully visible
 * @property fadeOut time to disappear
 * @property serializerType text format override, or `null` for the default
 * @property target who sees it
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

    override fun run(context: ActionContext): ActionResult {
        context.target(target).showTitle(
            Title.title(
                context.component(title, serializerType),
                context.component(subtitle, serializerType),
                Title.Times.times(fadeIn, stay, fadeOut),
            ),
        )
        return ActionResult.Done
    }
}
