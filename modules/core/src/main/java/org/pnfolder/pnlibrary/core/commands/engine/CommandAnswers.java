package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.commands.error.NoPermission;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;

/** The error waterfall: deepest node, parent nodes, module, plugin, then the library standard. */
final class CommandAnswers {
    private final StandardCommandFeedback standard;
    private final PlatformLogging logging;

    CommandAnswers(StandardCommandFeedback standard, PlatformLogging logging) {
        this.standard = standard;
        this.logging = logging;
    }

    // At each level the handler for the exact error class wins, then the nearest base class.
    void answer(RegisteredCommand registration, CommandContext context, CommandError error, List<CommandNode> nodes) {
        for (CommandNode node : nodes) {
            if (handledBy(registration, context, error, node.getErrorHandlers())) {
                return;
            }
            if (error instanceof NoPermission && node.getNoPermissionMessage() != null
                    && warn(registration, context, node.getNoPermissionMessage())) {
                return;
            }
        }
        for (CommandFeedbackScope scope = registration.getFeedback(); scope != null; scope = scope.getParent()) {
            if (handledBy(registration, context, error, scope.getHandlers())) {
                return;
            }
        }
        final List<String> help = helpFor(nodes.isEmpty() ? null : nodes.get(0), context);
        react(registration, () -> standard.answer(context, error, help));
    }

    private boolean warn(RegisteredCommand registration, final CommandContext context, final String text) {
        return react(registration, () -> SenderNotices.notice(context.getSender(), ChatTone.WARNING, text, null));
    }

    private boolean handledBy(RegisteredCommand registration, final CommandContext context, final CommandError error,
            Map<Class<? extends CommandError>, CommandErrorHandler<?>> handlers) {
        final CommandErrorHandler<CommandError> handler = CommandFeedbackScope.handlerFor(handlers, error);
        if (handler == null) {
            return false;
        }
        return react(registration, () -> handler.handle(context, error));
    }

    // A broken handler logs and returns false so the next level answers: the sender never sees handler exceptions.
    private boolean react(RegisteredCommand registration, Runnable reaction) {
        try {
            reaction.run();
            return true;
        } catch (Exception error) {
            logging.log(registration.getOwner(), LogLevel.ERROR, "Command feedback handler failed", error);
            return false;
        }
    }

    private List<String> helpFor(CommandNode node, CommandContext context) {
        if (node == null) {
            return Collections.emptyList();
        }
        List<String> help = new ArrayList<>(node.getDescription());
        for (CommandNode child : node.getChildren()) {
            if (!child.getDescription().isEmpty() && visibleTo(child, context)) {
                String name = child.getKind() == CommandNodeKind.ARGUMENT ? "<" + child.getName() + ">" : child.getName();
                help.add(name + " — " + child.getDescription().get(0));
            }
        }
        return help;
    }

    private boolean visibleTo(CommandNode child, CommandContext context) {
        String needed = child.getPermission();
        if (needed != null && !AudienceExtensions.hasPermission(context.getSender(), needed)) {
            return false;
        }
        boolean console = AudienceExtensions.isConsole(context.getSender());
        switch (child.getSenderRule()) {
            case PLAYER:
                return !console;
            case CONSOLE:
                return console;
            default:
                return true;
        }
    }
}
