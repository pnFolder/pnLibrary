package org.pnfolder.pnlibrary.bukkit.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.pnfolder.pnlibrary.common.lang.Pair;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages;

class BukkitControlCommandTest {
    @Test
    void controlDefinitionExposesAdminCommandAndActionsThroughSharedBuilder() {
        CommandDefinition definition = new BukkitControlCommand(proxy(Plugin.class), proxy(PnLibrary.class),
            proxy(CurrencyProviderRegistry.class)).definition();

        assertEquals("pn", definition.getName());
        // The root has no permission of its own: every administrative word asks for pnlibrary.admin by itself.
        assertNull(definition.getPermission());
        assertEquals(Arrays.asList("status", "check", "gui", "updates", "update", "support", "bus", "tasks", "modules",
            "configs", "deps", "servers", "money"), names(definition.getRoot().getChildren()));
        Set<String> hidden = new HashSet<>(Arrays.asList("money", "bus", "tasks", "modules", "configs", "deps", "servers"));
        List<CommandNode> administrative = new ArrayList<>();
        for (CommandNode child : definition.getRoot().getChildren()) {
            if (!hidden.contains(child.getName())) {
                administrative.add(child);
            }
        }
        for (CommandNode node : administrative) {
            assertEquals("pnlibrary.admin", node.getPermission(), node.getName());
        }
        CommandNode update = child(definition.getRoot().getChildren(), "update");
        // A narrower permission opens the same word next to the wide one.
        assertEquals(Collections.singletonList("pnlibrary.updates.download"), update.getAlsoPermissions());
        CommandNode catalog = child(definition.getRoot().getChildren(), "updates");
        assertEquals(Arrays.asList("list", "available", "info", "settings", "set", "history", "backups", "rollback"),
            names(catalog.getChildren()));
        assertEquals(Arrays.asList("page", "plugin"), names(child(catalog.getChildren(), "history").getChildren()));
        assertEquals("plugin", child(catalog.getChildren(), "info").getChildren().get(0).getName());
        assertEquals(Arrays.asList("plugin", "confirm", "rollback", "restart"), names(update.getChildren()));
        assertEquals(CommandNodeKind.ARGUMENT, update.getChildren().get(0).getKind());
        assertEquals("token", child(update.getChildren(), "confirm").getChildren().get(0).getName());
    }

    @Test
    void updateStatusMessagesExplainCurrentPausedBlockedFailedAndAvailableStates() {
        ProductId product = ProductId.of("acceptance");
        BlockedReason blocked = new BlockedReason.NoCompatibleRelease(product, 2);

        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.SUCCESS, "Плагин acceptance уже использует актуальную версию.")),
            status(snapshot(UpdateState.UP_TO_DATE, null, Collections.<BlockedReason>emptyList(), null, false)));
        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.WARNING, "Обновления плагина acceptance временно приостановлены.")),
            status(snapshot(UpdateState.FROZEN, null, Collections.<BlockedReason>emptyList(), null, false)));
        assertEquals(Arrays.asList(
            new ChatNotice(ChatTone.FAILURE, "Обновление плагина acceptance заблокировано."),
            new ChatNotice(ChatTone.WARNING, "Причина: для плагина acceptance нет версии, совместимой с pnLibrary API 2.")),
            status(snapshot(UpdateState.BLOCKED, null, Collections.singletonList(blocked), null, false)));
        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.FAILURE,
            "Не удалось проверить обновление плагина acceptance.", "GitHub вернул HTTP 404.")),
            status(snapshot(UpdateState.FAILED, null, Collections.<BlockedReason>emptyList(), "GitHub вернул HTTP 404", false)));
        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.INFO,
            "Для плагина acceptance доступно обновление: v2.2.0 » v2.5.0.")),
            status(snapshot(UpdateState.UPDATE_AVAILABLE, "2.5.0", Collections.<BlockedReason>emptyList(), null, true)));
        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.SUCCESS, "Плагин acceptance уже использует актуальную версию.")),
            status(snapshot(UpdateState.UPDATE_STAGED, "2.5.0", Collections.<BlockedReason>emptyList(), null, false)));
        assertEquals(Collections.singletonList(new ChatNotice(ChatTone.SUCCESS,
            "Обновление плагина acceptance проверено и подготовлено.", "Полностью перезапустите сервер.")),
            status(snapshot(UpdateState.UPDATE_STAGED, "2.5.0", Collections.<BlockedReason>emptyList(), null, true)));
    }

    private static List<ChatNotice> status(Pair<UpdatePlanSnapshot, UpdateSnapshot> pair) {
        return UpdateStatusMessages.status(pair.getFirst(), pair.getSecond());
    }

    private static List<String> names(List<CommandNode> nodes) {
        List<String> names = new ArrayList<>();
        for (CommandNode node : nodes) {
            names.add(node.getName());
        }
        return names;
    }

    private static CommandNode child(List<CommandNode> nodes, String name) {
        for (CommandNode node : nodes) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        throw new IllegalStateException("No node " + name);
    }

    private static Pair<UpdatePlanSnapshot, UpdateSnapshot> snapshot(UpdateState state, String latest,
                                                                     List<BlockedReason> blockers, String message,
                                                                     boolean changed) {
        UpdatePlan plan = changed ? new UpdatePlan(PnLibraryApi.VERSION,
            Collections.singletonList(new ProductChange(ProductId.of("acceptance"), SemanticVersion.parse("2.2.0"),
                SemanticVersion.parse(latest != null ? latest : "2.5.0"))),
            Collections.<org.pnfolder.pnlibrary.api.updates.release.ProductRelease>emptyList()) : null;
        return new Pair<>(new UpdatePlanSnapshot(UUID.randomUUID(), 1, state, plan, blockers, message),
            new UpdateSnapshot("acceptance", "2.2.0", latest, UpdateChannel.STABLE, state, 17, 8, false, null, message));
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return type.getSimpleName();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return false;
                default:
                    if (method.getReturnType() == Boolean.TYPE) {
                        return false;
                    }
                    if (method.getReturnType() == Integer.TYPE) {
                        return 0;
                    }
                    return method.getReturnType() == Long.TYPE ? (Object) 0L : null;
            }
        }));
    }
}
