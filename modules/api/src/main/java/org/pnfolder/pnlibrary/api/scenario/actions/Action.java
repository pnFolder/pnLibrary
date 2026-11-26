package org.pnfolder.pnlibrary.api.scenario.actions;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.MessageAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.ActionBarAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.TitleAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.SoundAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.CommandAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.LogAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.EffectAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.ParticleAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.DelayAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RepeatAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.IfAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RequireAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.AttemptAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.StopAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.SetAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.CallAction;
import org.pnfolder.pnlibrary.api.scenario.actions.builtin.RandomAction;

/**
 * One step of a configured scenario, such as sending a message, playing a sound or taking money.
 *
 * <p>A scenario is a list of actions written in a configuration file; each action has a {@code type}. Actions run
 * in order. Each one reports an {@link ActionResult}: the list goes on after {@link ActionResult.Done} and ends
 * early after {@link ActionResult.Stopped} (a choice) or {@link ActionResult.Failed} (an error that undoes earlier
 * steps).
 *
 * <pre>{@code
 * on-buy:
 *   - type: require                 # stops quietly when the requirement is not met
 *     when: { type: permission, permission: shop.use }
 *     deny:
 *       - { type: message, messages: ['&cNo permission'] }
 *   - type: attempt                 # a failed charge shows a message instead of an error
 *     do:
 *       - { type: charge, currency: money, amount: 100 }
 *       - { type: command, command: 'give [player] diamond 1' }
 *     fail:
 *       - { type: message, messages: ['&cCannot pay: [error]'] }
 * }</pre>
 *
 * <p>Plugins may add their own types through the configuration type registry. In code an action is a lambda:
 *
 * <pre>{@code
 * Action greet = ctx -> {
 *     ctx.getPlayer().sendMessage(Component.text("Hello"));
 *     return ActionResult.Done.INSTANCE;
 * };
 * }</pre>
 *
 * <p>An action must keep to the services of {@link ActionContext} so that one parsed list can run many times, on
 * any platform. It should report a problem as {@link ActionResult.Failed} rather than throw; a thrown exception is
 * turned into a failure by the context.
 */
@ConfigPolymorphic(discriminator = "type")
@ConfigTypes({
        @ConfigType(type = MessageAction.class, name = "message", aliases = {"messages"}),
        @ConfigType(type = ActionBarAction.class, name = "actionbar", aliases = {"action-bar"}),
        @ConfigType(type = TitleAction.class, name = "title"),
        @ConfigType(type = SoundAction.class, name = "sound"),
        @ConfigType(type = CommandAction.class, name = "command"),
        @ConfigType(type = LogAction.class, name = "log"),
        @ConfigType(type = EffectAction.class, name = "effect"),
        @ConfigType(type = ParticleAction.class, name = "particle"),
        @ConfigType(type = DelayAction.class, name = "delay", aliases = {"wait"}),
        @ConfigType(type = RepeatAction.class, name = "repeat"),
        @ConfigType(type = IfAction.class, name = "if"),
        @ConfigType(type = RequireAction.class, name = "require"),
        @ConfigType(type = AttemptAction.class, name = "attempt"),
        @ConfigType(type = StopAction.class, name = "stop"),
        @ConfigType(type = SetAction.class, name = "set"),
        @ConfigType(type = CallAction.class, name = "call"),
        @ConfigType(type = RandomAction.class, name = "random")
})
@FunctionalInterface
public interface Action {
    /** Performs the step with the data and services in {@code context} and reports how it went. */
    @NotNull
    ActionResult run(@NotNull ActionContext context);
}
