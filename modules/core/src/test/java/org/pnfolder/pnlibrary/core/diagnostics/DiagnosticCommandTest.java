package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommandEvent;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommandRenderer;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommands;

class DiagnosticCommandTest {
    @Test
    void definitionContainsAllPortableMetadataAndFilteredSuggestions() {
        PnLibrary library = libraryWithPlugins(Arrays.asList(metadata("PnClans")));
        CommandDefinition definition = DiagnosticCommands.create(library);

        List<String> suggestions = definition.getSuggestions()
            .suggest(new CommandContext(new TestSender(), Collections.emptyList(), "debug", "example"))
            .toCompletableFuture().join();

        assertEquals("debug", definition.getName());
        assertEquals("pnlibrary.debug", definition.getPermission());
        assertTrue(definition.getConsoleBypassesPermission());
        assertEquals(Arrays.asList("example.pnclans"), suggestions);
    }

    @Test
    void suggestionsExposeTargetAndFlagsFromOneSharedDefinition() {
        PnLibrary library = libraryWithPlugins(Collections.emptyList());
        CommandDefinition definition = DiagnosticCommands.create(library);

        List<String> suggestions = definition.getSuggestions()
            .suggest(new CommandContext(new TestSender(), Collections.emptyList(), "debug", ""))
            .toCompletableFuture().join();

        assertEquals(Arrays.asList("all", "--local"), suggestions);
    }

    @Test
    void invalidInvocationRendersUsageWithoutAPlatformRenderer() {
        PnLibrary library = libraryWithPlugins(Collections.emptyList());
        TestSender sender = new TestSender();
        CommandDefinition definition = DiagnosticCommands.create(library);

        definition.getExecution()
            .execute(new CommandContext(sender, Arrays.asList("--unknown"), "debug", "--unknown"))
            .toCompletableFuture().join();

        assertEquals(Arrays.asList("  ! Неизвестные аргументы.", "    /pn debug [all|плагин] [--local]"),
            plain(sender.messages));
    }

    @Test
    void anUploadedReportOffersAButtonToOpenItTheConsoleGetsTheLinkOnly() {
        DiagnosticReport report = new DiagnosticReport(Paths.get("reports", "debug.json"), false,
            "https://paste.example/abc", null);
        DiagnosticCommandEvent event = new DiagnosticCommandEvent.Completed(report);

        List<Component> player = DiagnosticCommandRenderer.render(event, new ChatKit(ChatReader.PLAYER));
        List<Component> console = DiagnosticCommandRenderer.render(event, new ChatKit(ChatReader.CONSOLE));

        assertEquals(3, player.size());
        Component last = player.get(player.size() - 1);
        assertEquals(ClickEvent.openUrl("https://paste.example/abc"), last.children().get(0).clickEvent());
        assertEquals(Arrays.asList("  + Отчёт готов.", "    https://paste.example/abc"), plain(console));
    }

    @Test
    void aFailedUploadKeepsTheLocalReportAndSaysWhy() {
        DiagnosticReport report = new DiagnosticReport(Paths.get("debug.json"), false, null, "timeout");

        List<Component> lines = DiagnosticCommandRenderer.render(
            new DiagnosticCommandEvent.Completed(report), new ChatKit(ChatReader.CONSOLE));

        assertEquals(Arrays.asList("  ! Отчёт сохранён только локально: загрузить не удалось.",
            "    debug.json | timeout"), plain(lines));
    }

    private static List<String> plain(List<Component> components) {
        List<String> lines = new ArrayList<>();
        for (Component component : components) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(component));
        }
        return lines;
    }

    private static PluginMetadata metadata(String name) {
        return new PluginMetadata(ModuleId.of(name), name, "1.0.0", "pnFolder", PlatformType.BUKKIT, "Paper", "21", 21);
    }

    private static PnLibrary libraryWithPlugins(List<PluginMetadata> metadata) {
        final List<PluginRegistration> registrations = new ArrayList<>();
        for (final PluginMetadata value : metadata) {
            final ModuleContext module = proxy(ModuleContext.class, method -> {
                if (method.getName().equals("getMetadata")) {
                    return value;
                }
                if (method.getName().equals("getKey")) {
                    return PluginId.of("example." + value.getId().getValue());
                }
                return defaultValue(method.getReturnType());
            });
            registrations.add(proxy(PluginRegistration.class, method ->
                method.getName().equals("all") ? Collections.singletonList(module) : defaultValue(method.getReturnType())));
        }
        final PluginRegistry plugins = proxy(PluginRegistry.class, method ->
            method.getName().equals("all") ? registrations : defaultValue(method.getReturnType()));
        return proxy(PnLibrary.class, method ->
            method.getName().equals("getPlugins") ? plugins : defaultValue(method.getReturnType()));
    }

    private interface MethodHandler {
        Object handle(Method method);
    }

    private static <T> T proxy(Class<T> type, final MethodHandler handler) {
        InvocationHandler invocation = (proxy, method, args) -> handler.handle(method);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocation));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static final class TestSender implements CommandSender {
        final List<Component> messages = new ArrayList<>();

        @NotNull
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers("Console", permission -> true);
        }

        @Override
        public void sendMessage(@NotNull Component message) {
            messages.add(message);
        }
    }
}
