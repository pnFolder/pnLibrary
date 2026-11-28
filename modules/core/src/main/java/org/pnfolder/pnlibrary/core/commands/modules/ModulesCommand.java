package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.page.CommandHint;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/**
 * {@code /pn modules}: the modules of every plugin and their switches.
 *
 * <pre>
 * /pn modules                                                    every module with its state
 * /pn modules plugins                                            every plugin with its modules and the number of libraries
 * /pn modules libs [plugin|plugin:module]                        libraries with name, description, owner and version conflicts
 * /pn modules lib enable|disable|retry owner group:artifact:ver  switches one library (saved across restarts) or retries it
 * /pn modules mc force module on|off                             start the module although the Minecraft version is outside what it declared
 * /pn modules disk                                               what every plugin takes on the disk
 * /pn modules view module                                        one module: state, last error, whether it starts with the server
 * /pn modules enable|disable|restart|reload module               switches the module now
 * /pn modules auto module on|off                                 whether the module starts with the server; saved
 * </pre>
 *
 * A module is {@code plugin:module}, or just the module id when it is unique. Requires {@code pnlibrary.modules}; the
 * console always may use it. A module its plugin registered without hooks is listed but cannot be switched.
 */
public final class ModulesCommand {
    public static final String PERMISSION = "pnlibrary.modules";
    public static final String ROOT = "/pn modules";

    private final PnLibrary library;
    private final LibraryPages libraries;
    private final ModuleCard card;
    private final DiskPage disk;

    public ModulesCommand(@NotNull PnLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
        this.libraries = new LibraryPages(library);
        this.card = new ModuleCard(library);
        this.disk = new DiskPage(library);
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("modules", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Модули плагинов доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(this::list);
            libraryWords(root);
            root.literal("mc", mc -> mc.literal("force", force -> switchWords(force,
                    (context, on) -> forceMinecraft(context, context.<String>get("module"), on))));
            root.literal("plugins", node -> node.executes(this::plugins));
            root.literal("disk", node -> node.executes(disk::show));
            root.literal("view", node -> moduleArgument(node, this::view));
            for (final ModuleAction action : ModuleAction.values()) {
                root.literal(action.getWord(), node -> moduleArgument(node, (context, typed) -> change(context, typed, action)));
            }
            root.literal("auto", auto -> switchWords(auto, (context, on) -> auto(context, context.<String>get("module"), on)));
        });
    }

    private void libraryWords(CommandBuilder root) {
        root.literal("libs", node -> {
            node.executes(context -> libraries.libs(context, null));
            node.argument("owner", ArgumentType.string(), owner -> {
                owner.suggests(context -> owners());
                owner.executes(context -> libraries.libs(context, context.<String>get("owner")));
            });
        });
        root.literal("lib", node -> {
            for (final String action : new String[] {"enable", "disable", "retry"}) {
                node.literal(action, inner -> inner.argument("owner", ArgumentType.string(), owner -> {
                    owner.suggests(context -> owners());
                    owner.argument("library", ArgumentType.string(), coordinates -> {
                        coordinates.suggests(context -> coordinateList());
                        coordinates.executes(context -> libraries.changeLibrary(
                                context, action, context.<String>get("owner"), context.<String>get("library")));
                    });
                }));
            }
        });
    }

    private List<String> owners() {
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        for (LibraryRow row : LibraryRow.all(library)) {
            owners.add(row.getOwner());
        }
        return new ArrayList<>(owners);
    }

    private List<String> coordinateList() {
        LinkedHashSet<String> coordinates = new LinkedHashSet<>();
        for (LibraryRow row : LibraryRow.all(library)) {
            coordinates.add(row.getCoordinates());
        }
        return new ArrayList<>(coordinates);
    }

    /** {@code <module> on|off}: the words that turn a per-module switch on or off. */
    private void switchWords(CommandNodeBuilder node, final BiConsumer<CommandContext, Boolean> run) {
        node.argument("module", ArgumentType.string(), module -> {
            module.suggests(context -> names());
            module.literal("on", on -> on.executes(context -> run.accept(context, true)));
            module.literal("off", off -> off.executes(context -> run.accept(context, false)));
        });
    }

    private void moduleArgument(CommandNodeBuilder node, final BiConsumer<CommandContext, String> run) {
        node.argument("module", ArgumentType.string(), module -> {
            module.suggests(context -> names());
            module.executes(context -> run.accept(context, context.<String>get("module")));
        });
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (NamedModule module : ModuleLabels.named(library)) {
            names.add(module.getName());
        }
        return names;
    }

    /** The module typed as a full name or as a unique module id; tells the sender and returns null when there is none. */
    private NamedModule find(CommandContext context, String typed) {
        List<NamedModule> all = ModuleLabels.named(library);
        for (NamedModule module : all) {
            if (module.getName().equalsIgnoreCase(typed)) {
                return module;
            }
        }
        NamedModule single = null;
        int matches = 0;
        for (NamedModule module : all) {
            if (module.getModule().getId().getValue().equalsIgnoreCase(typed)) {
                single = module;
                matches++;
            }
        }
        if (matches == 1) {
            return single;
        }
        context.getSender().sendMessage(CommandOutput.kit(context)
                .text("Такого модуля нет или имя неоднозначно: " + typed + ". Список: " + ROOT, ChatPalette.DANGER));
        return null;
    }

    private void list(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        List<NamedModule> all = ModuleLabels.named(library);
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Модули плагинов", kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        if (all.isEmpty()) {
            lines.add(kit.note("Модулей пока нет.", ChatPalette.FAINT));
        }
        for (NamedModule named : all) {
            Component row = moduleRow(kit, named);
            lines.add(kit.getReader().isInteractive() ? row.clickEvent(ClickEvent.runCommand(ROOT + " view " + named.getName())) : row);
        }
        if (!kit.getReader().isInteractive()) {
            lines.add(kit.blank());
            lines.addAll(kit.commandHints(
                    new CommandHint(ROOT + " view <модуль>", "карточка модуля"),
                    new CommandHint(ROOT + " enable|disable|restart|reload <модуль>", "переключить сейчас"),
                    new CommandHint(ROOT + " auto <модуль> on|off", "включать ли вместе с сервером")));
        }
        CommandOutput.send(context, lines);
    }

    private Component moduleRow(ChatKit kit, NamedModule named) {
        ModuleControl control = named.getModule().getControl();
        ModuleState state = control.getState();
        Component row = kit.text(" ").append(kit.text(kit.glyph('●'), ModuleLabels.color(state)))
                .append(kit.text(" " + named.getName(), ChatPalette.ACCENT))
                .append(kit.text("  " + ModuleLabels.label(state), ModuleLabels.color(state)));
        if (!control.isControllable()) {
            return row.append(kit.dot()).append(kit.text("без переключателя", ChatPalette.FAINT));
        }
        if (!control.getAutoStart()) {
            return row.append(kit.dot()).append(kit.text("не включается сам", ChatPalette.WARN));
        }
        return row;
    }

    private void forceMinecraft(CommandContext context, String typed, boolean on) {
        NamedModule found = find(context, typed);
        if (found == null) {
            return;
        }
        ChatKit kit = CommandOutput.kit(context);
        ModuleContext module = found.getModule();
        if (!(module instanceof MinecraftForcible) || module.getMinecraft().getDeclared() == null) {
            context.getSender().sendMessage(kit.note("У " + found.getName()
                    + " не объявлены версии Minecraft или он не из этого сервера.", ChatPalette.WARN));
            return;
        }
        try {
            ((MinecraftForcible) module).forceMinecraft(on);
        } catch (IllegalStateException forbidden) {
            context.getSender().sendMessage(kit.note("Автор плагина запретил запускать " + found.getName()
                    + " на этой версии Minecraft. Обновите сервер или плагин.", ChatPalette.DANGER));
            return;
        }
        String text = on ? "запускать при любой версии Minecraft (на свой риск)" : "версия Minecraft снова проверяется";
        context.getSender().sendMessage(kit.text(found.getName() + ": ", ChatPalette.TEXT).append(kit.text(text, ChatPalette.SOFT)));
    }

    private void view(CommandContext context, String typed) {
        NamedModule found = find(context, typed);
        if (found != null) {
            card.show(context, found);
        }
    }

    private void plugins(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        List<PluginRegistration> all = new ArrayList<>(library.getPlugins().all());
        java.util.Collections.sort(all, (left, right) -> left.getId().getValue().compareTo(right.getId().getValue()));
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Плагины библиотеки", kit.text(String.valueOf(all.size()), ChatPalette.LABEL)));
        for (PluginRegistration plugin : all) {
            List<ModuleContext> modules = plugin.all();
            lines.add(kit.text(" " + plugin.getId().getValue(), ChatPalette.ACCENT)
                    .append(kit.dot()).append(kit.text("модулей " + modules.size(), ChatPalette.SOFT))
                    .append(kit.dot()).append(kit.text("библиотек " + plugin.getAllLibraries().size(), ChatPalette.SOFT)));
            for (ModuleContext module : modules) {
                ModuleState state = module.getControl().getState();
                lines.add(kit.text("    " + module.getId().getValue(), ChatPalette.TEXT)
                        .append(kit.text("  " + ModuleLabels.label(state), ModuleLabels.color(state))));
            }
        }
        CommandOutput.send(context, lines);
    }

    private void change(final CommandContext context, String typed, ModuleAction action) {
        final NamedModule found = find(context, typed);
        if (found == null) {
            return;
        }
        final ChatKit kit = CommandOutput.kit(context);
        final ModuleControl control = found.getModule().getControl();
        if (!control.isControllable()) {
            context.getSender().sendMessage(kit.text("У модуля " + found.getName() + " нет методов включения и выключения.",
                    ChatPalette.DANGER));
            return;
        }
        action.run(control).whenComplete((ignored, error) -> {
            Throwable failed = control.getLastError();
            context.getSender().sendMessage(failed != null
                    ? kit.text("Модуль " + found.getName() + ": ошибка, "
                            + (failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage()), ChatPalette.DANGER)
                    : kit.text("Модуль " + found.getName() + ": " + ModuleLabels.label(control.getState()), ChatPalette.OK));
        });
    }

    private void auto(CommandContext context, String typed, boolean enabled) {
        NamedModule found = find(context, typed);
        if (found == null) {
            return;
        }
        ChatKit kit = CommandOutput.kit(context);
        ModuleControl control = found.getModule().getControl();
        if (!control.isControllable()) {
            context.getSender().sendMessage(kit.text("У модуля " + found.getName() + " нет методов включения и выключения.",
                    ChatPalette.DANGER));
            return;
        }
        control.setAutoStart(enabled);
        String text = enabled ? "будет включаться вместе с сервером" : "не будет включаться вместе с сервером";
        context.getSender().sendMessage(kit.text("Модуль " + found.getName() + " " + text + ".", ChatPalette.OK));
    }
}
