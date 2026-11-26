package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.commands.engine.CommandRegistryTest.context;
import static org.pnfolder.pnlibrary.core.commands.engine.TestCommandSender.textOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.error.BadUsage;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler;
import org.pnfolder.pnlibrary.api.commands.error.CommandErrors;
import org.pnfolder.pnlibrary.api.commands.error.InvalidArgument;
import org.pnfolder.pnlibrary.api.commands.error.NoPermission;

class CommandFeedbackTest {
    private static final String CONFIGURED = "Настроенный текст из config.yml";
    private static CommandErrorHandler<CommandError> say(final String text) {
        return (context, error) -> context.getSender().sendMessage(Component.text(error.format(text)));
    }

    private static CommandRegistry service(RecordingCommands nativeCommands, StandardCommandFeedback standard) {
        return new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands), standard);
    }

    private static CommandRegistry service(RecordingCommands nativeCommands) {
        return service(nativeCommands, new StandardCommandFeedback());
    }

    private static void run(RecordingCommands nativeCommands, TestCommandSender sender, String... arguments) {
        nativeCommands.dispatcher.execute(nativeCommands.command, context(sender, arguments)).toCompletableFuture().join();
    }

    @Test
    @DisplayName("a subcommand message wins over the command message and the configured default")
    void aSubcommandMessageWinsOverTheCommandMessageAndTheConfiguredDefault() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands, new StandardCommandFeedback(CONFIGURED)).register(new Object(), CommandDefinition.command("hello", root -> {
            root.noPermissionMessage("Текст команды");
            root.executes(context -> { });
            root.literal("vip", vip -> {
                vip.permission("example.vip");
                vip.noPermissionMessage("Это только для VIP");
                vip.executes(context -> { });
            });
            root.literal("staff", staff -> {
                staff.permission("example.staff");
                staff.executes(context -> { });
            });
        }));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender, "vip");
        run(nativeCommands, sender, "staff");

        assertEquals(Arrays.asList("Это только для VIP", "Текст команды"), sender.texts());
    }

    @Test
    @DisplayName("the configured default replaces the built in text when nothing more specific is set")
    void theConfiguredDefaultReplacesTheBuiltInTextWhenNothingMoreSpecificIsSet() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands, new StandardCommandFeedback(CONFIGURED)).register(new Object(), CommandDefinition.command("hello", root -> {
            root.permission("example.hello");
            root.executes(context -> { });
        }));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender);

        assertEquals(Arrays.asList(CONFIGURED), sender.texts());
    }

    @Test
    @DisplayName("a handler receives the typed error and the context, so the sender is right there")
    void aHandlerReceivesTheTypedErrorAndTheContextSoTheSenderIsRightThere() {
        RecordingCommands nativeCommands = new RecordingCommands();
        final List<String> seen = new ArrayList<String>();
        service(nativeCommands).register(new Object(), CommandDefinition.command("hello", root -> {
            root.executes(context -> { });
            root.onError(NoPermission.class, (context, error) ->
                    seen.add("command:" + AudienceExtensions.id(context.getSender()) + ":" + error.getPermission()));
            root.literal("vip", vip -> {
                vip.permission("example.vip");
                vip.onError(NoPermission.class, (context, error) -> {
                    seen.add("node:" + error.getPermission());
                    context.getSender().sendMessage(Component.text("Свой ответ"));
                });
                vip.executes(context -> { });
            });
            root.literal("staff", staff -> {
                staff.permission("example.staff");
                staff.executes(context -> { });
            });
        }));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender, "vip");
        run(nativeCommands, sender, "staff");

        assertEquals(2, seen.size());
        assertEquals("node:example.vip", seen.get(0));
        assertTrue(seen.get(1).startsWith("command:") && seen.get(1).endsWith(":example.staff"));
        assertEquals(Arrays.asList("Свой ответ"), sender.texts());
    }

    @Test
    @DisplayName("a handler that fails is logged and the next level answers, never an error shown to the sender")
    void aHandlerThatFailsIsLoggedAndTheNextLevelAnswersNeverAnErrorShownToTheSender() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands, new StandardCommandFeedback("Настроенный текст")).register(new Object(), CommandDefinition.command("hello", root -> {
            root.permission("example.hello");
            root.executes(context -> { });
            root.onError(NoPermission.class, (context, error) -> {
                throw new IllegalStateException("handler is broken");
            });
        }));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender);

        assertEquals(Arrays.asList("Настроенный текст"), sender.texts());
    }

    @Test
    @DisplayName("the chain goes from subcommand to command to module to plugin to the library standard")
    void theChainGoesFromSubcommandToCommandToModuleToPluginToTheLibraryStandard() {
        RecordingCommands nativeCommands = new RecordingCommands();
        CommandFeedbackScope plugin = new CommandFeedbackScope();
        CommandFeedbackScope module = new CommandFeedbackScope(plugin);
        module.getHandlers().put(NoPermission.class, say("Текст модуля"));
        plugin.getHandlers().put(NoPermission.class, say("Текст плагина"));
        TestCommandSender sender = new TestCommandSender();
        service(nativeCommands, new StandardCommandFeedback("Стандарт библиотеки")).register(new Object(),
                CommandDefinition.command("hello", root -> {
                    root.executes(context -> { });
                    root.literal("node", node -> {
                        node.permission("p.node");
                        node.noPermissionMessage("Текст узла");
                        node.executes(context -> { });
                    });
                    root.literal("module", node -> {
                        node.permission("p.module");
                        node.executes(context -> { });
                    });
                }), module);

        run(nativeCommands, sender, "node");
        run(nativeCommands, sender, "module");
        module.getHandlers().remove(NoPermission.class);
        run(nativeCommands, sender, "module");
        plugin.getHandlers().remove(NoPermission.class);
        run(nativeCommands, sender, "module");

        assertEquals(Arrays.asList("Текст узла", "Текст модуля", "Текст плагина", "Стандарт библиотеки"), sender.texts());
    }

    @Test
    @DisplayName("a sender who stops at a command is told what it does under the usage line")
    void aSenderWhoStopsAtACommandIsToldWhatItDoesUnderTheUsageLine() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands).register(new Object(), CommandDefinition.command("hello", root -> root.literal("make", make -> {
            make.description("Делает группу.");
            make.argument("name", ArgumentType.string(), name -> name.executes(context -> { }));
        })));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender, "make");

        assertEquals(Arrays.asList("Использование: /hello make <name>", "    Делает группу."), sender.texts());
    }

    @Test
    @DisplayName("the standard answer can add a sound and reshape the usage line")
    void theStandardAnswerCanAddASoundAndReshapeTheUsageLine() {
        RecordingCommands nativeCommands = new RecordingCommands();
        Sound sound = Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1f, 1f);
        service(nativeCommands, new StandardCommandFeedback("Нет прав", sound, "Подсказка → {usage}")).register(new Object(),
                CommandDefinition.command("hello", root -> {
                    root.permission("p.hello");
                    root.executes(context -> { });
                    root.literal("a", a -> a.executes(context -> { }));
                }));
        TestCommandSender denied = new TestCommandSender();
        TestCommandSender allowed = new TestCommandSender(new HashSet<String>(Arrays.asList("p.hello")));

        run(nativeCommands, denied);
        run(nativeCommands, allowed, "zzz");

        assertEquals(Arrays.asList("Нет прав"), denied.texts());
        assertEquals(Collections.singletonList(sound), denied.sounds);
        assertTrue(textOf(allowed.messages.get(0)).startsWith("Подсказка → /hello"));
    }

    @Test
    @DisplayName("a handler for a base class answers every error derived from it until something more specific is set")
    void aHandlerForABaseClassAnswersEveryErrorDerivedFromItUntilSomethingMoreSpecificIsSet() {
        RecordingCommands plainCommands = new RecordingCommands();
        TestCommandSender plain = new TestCommandSender();
        service(plainCommands).register(new Object(), CommandDefinition.command("hello", root -> root.literal("buy",
                buy -> buy.argument("amount", ArgumentType.integer(1, 5), amount -> amount.executes(context -> { })))));
        run(plainCommands, plain, "buy", "99");
        assertEquals("Неверное значение «99» для amount. /hello buy <amount>", textOf(plain.messages.get(0)));

        TestCommandSender caught = new TestCommandSender();
        RecordingCommands second = new RecordingCommands();
        service(second).register(new Object(), CommandDefinition.command("hello", root -> {
            root.onError(BadUsage.class, (context, error) ->
                    context.getSender().sendMessage(Component.text("общий: " + error.getClass().getSimpleName())));
            root.literal("buy", buy -> buy.argument("amount", ArgumentType.integer(1, 5), amount -> amount.executes(context -> { })));
        }));
        run(second, caught, "buy", "99");
        run(second, caught, "zzz");
        run(second, caught, "buy");
        assertEquals(Arrays.asList("общий: InvalidArgument", "общий: UnknownArgument", "общий: IncompleteCommand"), caught.texts());

        TestCommandSender exact = new TestCommandSender();
        RecordingCommands third = new RecordingCommands();
        service(third).register(new Object(), CommandDefinition.command("hello", root -> {
            root.onError(BadUsage.class, (context, error) -> context.getSender().sendMessage(Component.text("общий")));
            root.onError(InvalidArgument.class, "точный: {argument}={value}");
            root.literal("buy", buy -> buy.argument("amount", ArgumentType.integer(1, 5), amount -> amount.executes(context -> { })));
        }));
        run(third, exact, "buy", "x");
        assertEquals("точный: amount=x", textOf(exact.messages.get(0)));
    }

    @Test
    @DisplayName("a player argument that names nobody is reported as player not found")
    void aPlayerArgumentThatNamesNobodyIsReportedAsPlayerNotFound() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands).register(new Object(), CommandDefinition.command("hello", root -> root.literal("kick",
                kick -> kick.argument("who", ArgumentType.player(name -> null), who -> who.executes(context -> { })))));
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender, "kick", "Steve");

        assertEquals("Игрок Steve сейчас не в сети.", textOf(sender.messages.get(0)));
    }

    @Test
    @DisplayName("player only and console only commands answer the wrong sender")
    void playerOnlyAndConsoleOnlyCommandsAnswerTheWrongSender() {
        RecordingCommands nativeCommands = new RecordingCommands();
        service(nativeCommands).register(new Object(), CommandDefinition.command("hello", root -> {
            root.literal("game", game -> {
                game.playerOnly();
                game.executes(context -> { });
            });
            root.literal("admin", admin -> {
                admin.consoleOnly();
                admin.executes(context -> { });
            });
        }));
        TestCommandSender console = new TestCommandSender(true);
        TestCommandSender player = new TestCommandSender();

        run(nativeCommands, console, "game");
        run(nativeCommands, player, "admin");

        assertEquals("Эту команду можно использовать только в игре.", textOf(console.messages.get(0)));
        assertEquals("Эту команду можно использовать только из консоли.", textOf(player.messages.get(0)));
    }

    @Test
    @DisplayName("a command stops with an error of its own and any level can answer it")
    void aCommandStopsWithAnErrorOfItsOwnAndAnyLevelCanAnswerIt() {
        RecordingCommands nativeCommands = new RecordingCommands();
        CommandFeedbackScope module = new CommandFeedbackScope();
        CommandErrorHandler<NoMoneyError> noMoney = (context, error) ->
                context.getSender().sendMessage(Component.text("Не хватает " + error.getPrice() + " монет"));
        module.getHandlers().put(NoMoneyError.class, noMoney);
        service(nativeCommands).register(new Object(), CommandDefinition.command("hello", root -> {
            root.executes(context -> CommandErrors.ensure(false, () -> new NoMoneyError(50)));
            root.literal("closed", closed -> closed.executes(context -> CommandErrors.fail(new ShopClosedError())));
            root.literal("odd", odd -> odd.executes(context -> CommandErrors.fail(new UnexplainedError())));
            root.literal("fine", fine -> fine.executes(context -> CommandErrors.ensure(true, () -> new NoMoneyError(1))));
        }), module);
        TestCommandSender sender = new TestCommandSender();

        run(nativeCommands, sender);
        run(nativeCommands, sender, "closed");
        run(nativeCommands, sender, "odd");
        run(nativeCommands, sender, "fine");

        assertEquals(Arrays.asList("Не хватает 50 монет", "Магазин закрыт", "Не удалось выполнить команду."), sender.texts());
    }
}
