package org.pnfolder.pnlibrary.core.menu.data;

import org.pnfolder.pnlibrary.core.menu.data.question.EventAbout;
import org.pnfolder.pnlibrary.core.menu.data.remote.MenuWire;
import org.pnfolder.pnlibrary.core.menu.data.remote.RemoteMenuData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.link.wire.DataChunks;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

class MenuDataTest {
    private final byte[] key = key();
    private final Instant now = Instant.parse("2026-10-30T12:00:00Z");

    private final UpdateSnapshot snapshot = new UpdateSnapshot("shop", "1.0.0", "1.1.0", UpdateChannel.BETA, UpdateState.UPDATE_AVAILABLE,
        21, 17, true, "https://example.org/r", "ok", null, new ApiVersionRange(1, 2),
        Collections.singletonList(new ReleaseSummary("1.1.0", UpdateChannel.STABLE, now, "https://example.org/c")),
        Collections.emptyList(), null, null);
    private final UpdateHistoryEntry entry = new UpdateHistoryEntry(UUID.randomUUID(), now, UUID.randomUUID(), UpdateState.UPDATE_STAGED,
        new UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin"), "stage", Collections.singletonList(new ProductChange(ProductId.of("shop"),
        SemanticVersion.parse("1.0.0"), SemanticVersion.parse("1.1.0"))), "text");
    private final UpdateBackup backup = new UpdateBackup(UUID.randomUUID(), now, UUID.randomUUID(),
        Collections.singletonList(new UpdateBackupComponent(ProductId.of("shop"), "1.1.0", "1.0.0")), 3);
    private final TaskSnapshot task = TaskSnapshot.builder(new TaskId("shop:save"), "shop", TaskExecution.Kind.ASYNC, TaskStatus.SCHEDULED, now)
        .name("save").key("key").tags(Collections.singleton("a")).nextRunAt(now).runCount(4).skippedCount(1)
        .lastStartedAt(now).lastCompletedAt(now).description("d").usage("u").source("s")
        .interval(Duration.ofMinutes(5)).lastDuration(Duration.ofMillis(30))
        .cancellable(false).runnableNow(true).hidden(false).build();
    private final ListenerInfo listener = new ListenerInfo(PluginId.of("shop"), ModuleId.of("core"), "a.b.Event", 3, true, "x", 10, 1, 500, 900);
    private final EventTrace trace = new EventTrace("a.b.Event", true, "ok",
        Collections.singletonList(new EventTrace.Step(PluginId.of("shop"), null, 1, false, false, 5, "a", "b")), DispatchMode.SYNC, "", now);

    private static byte[] key() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) index;
        }
        return key;
    }

    private static <T> T roundTrip(Object value, Type type) {
        return MenuWire.gson().fromJson(MenuWire.gson().toJson(value), type);
    }

    private static <T> T single(Object value, Class<?> element) {
        List<T> list = roundTrip(Collections.singletonList(value), MenuWire.listOf(element));
        return list.get(0);
    }

    @Test
    void updateDataSurvivesTheTripAsJson() {
        UpdateSnapshot back = single(snapshot, UpdateSnapshot.class);
        assertEquals("shop", back.getProduct());
        assertEquals(UpdateState.UPDATE_AVAILABLE, back.getState());
        assertEquals(new ApiVersionRange(1, 2), back.getSupportedApi());
        assertEquals(now, back.getAvailableReleases().get(0).getPublishedAt());

        UpdateHistoryEntry history = single(entry, UpdateHistoryEntry.class);
        assertEquals(entry.getId(), history.getId());
        assertEquals("shop", history.getChanges().get(0).getProduct().getValue());
        assertEquals(SemanticVersion.parse("1.1.0"), history.getChanges().get(0).getTo());
        assertEquals("Admin", history.getActor().getName());

        UpdateBackup saved = single(backup, UpdateBackup.class);
        assertEquals("1.0.0", saved.getComponents().get(0).getBackupVersion());
        assertEquals(3, saved.getConfigurationFiles());
    }

    @Test
    void taskAndEventDataSurviveTheTripAsJson() {
        TaskSnapshot tasks = single(task, TaskSnapshot.class);
        assertEquals("shop:save", tasks.getId().getValue());
        assertEquals(Duration.ofMinutes(5), tasks.getInterval());
        assertEquals(Collections.singleton("a"), tasks.getTags());
        assertFalse(tasks.getCancellable());

        ListenerInfo listeners = single(listener, ListenerInfo.class);
        assertEquals("shop", listeners.getPlugin().getValue());
        assertEquals("core", listeners.getModule().getValue());
        assertEquals(900, listeners.getMaxNanos());

        EventTrace traces = single(trace, EventTrace.class);
        assertEquals("shop", traces.getSteps().get(0).getPlugin().getValue());
        assertEquals(now, traces.getAt());

        EventDay day = single(new EventDay(LocalDate.of(2026, 10, 30), "a.b.Event", 5, 1), EventDay.class);
        assertEquals(LocalDate.of(2026, 10, 30), day.getDate());

        EventAbout about = roundTrip(new EventAbout("p", "u", new OwnerInfo("shop", "1.0", Collections.singletonList("me"))), EventAbout.class);
        assertEquals(Collections.singletonList("me"), about.getOwner().getAuthors());

        PlanInfo plan = roundTrip(new PlanInfo(UpdateState.BLOCKED, true, Collections.singletonList("busy")), PlanInfo.class);
        assertEquals(Collections.singletonList("busy"), plan.getBlockers());
    }

    private static Map<String, Object> answers(Object... pairs) {
        Map<String, Object> answers = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            answers.put((String) pairs[index], pairs[index + 1]);
        }
        return answers;
    }

    /** A server whose facts are canned, and whose answers travel through the real link code. */
    private RemoteMenuData connected(final Map<String, Object> canned, BiPredicate<String, String> holds) {
        MenuData data = (MenuData) Proxy.newProxyInstance(MenuData.class.getClassLoader(), new Class<?>[] {MenuData.class},
            (proxy, method, args) -> {
                Object value = canned.get(method.getName());
                if (method.getName().equals("command")) {
                    return Collections.singletonList(Component.text("printed " + args[1]));
                }
                if (method.getReturnType() == CompletionStage.class) {
                    return CompletableFuture.completedFuture(value);
                }
                return method.getReturnType() == Void.TYPE ? null : value;
            });
        final MenuDataServer server = new MenuDataServer(data, Runnable::run, holds);
        final DataClient[] client = new DataClient[1];
        client[0] = new DataClient(key, bytes -> {
            final DataCall call = LinkCodec.decodeCall(bytes, key);
            server.handle(call, (ok, text) -> {
                for (byte[] chunk : DataChunks.split(call.getId(), "lobby", ok, text, key)) {
                    client[0].accept(LinkCodec.decodeChunk(chunk, key));
                }
            });
            return true;
        });
        return new RemoteMenuData("games", client[0]);
    }

    private RemoteMenuData connected(Map<String, Object> canned) {
        return connected(canned, (viewer, permission) -> true);
    }

    @Test
    void theMenuOfAnotherServerReadsTheFactsAndRunsItsActionsThroughTheLink() throws Exception {
        RemoteMenuData remote = connected(answers(
            "snapshots", Collections.singletonList(snapshot),
            "plan", new PlanInfo(UpdateState.UPDATE_STAGED, true, Collections.singletonList("busy")),
            "journalGroups", Collections.singletonList(new JournalGroup("g1", "error", "shop", "boom", 3, 1_000L)),
            "runTask", TaskControl.FORBIDDEN,
            "historyCount", 7,
            "checkAll", true));

        assertEquals("shop", remote.snapshots().get(0).getProduct());
        assertEquals(Collections.singletonList("busy"), remote.plan().getBlockers());
        assertEquals("boom", remote.journalGroups("error", 24, 10).get(0).getMessage());
        assertEquals(TaskControl.FORBIDDEN, remote.runTask("x"));
        assertEquals(7, (int) remote.historyCount(null).toCompletableFuture().get());
        assertTrue(remote.checkAll(new UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin")).toCompletableFuture().get());
        assertNull(remote.snapshot("none"));
    }

    @Test
    void thePlaceholdersOfAServerTravelThroughTheLink() {
        PlaceholderInfo info = new PlaceholderInfo("clan.war.score", "Score", "pnclans/war", "Integer", "number",
            Collections.singletonList("pnclans_clan_war_score"));

        assertEquals(Collections.singletonList(info), connected(answers("placeholders", Collections.singletonList(info))).placeholders());
    }

    @Test
    void theNamedActionsOfAServerTravelThroughTheLink() {
        ScenarioDefinitionInfo info = new ScenarioDefinitionInfo("action", "give-kit", "shop/main", "plugin");

        assertEquals(Collections.singletonList(info),
            connected(answers("scenarioDefinitions", Collections.singletonList(info))).scenarioDefinitions());
    }

    @Test
    void groupsWithTheDifferencesOfTheirServersTravelThroughTheLinkAndAreEditedThere() {
        Map<String, java.util.Set<String>> extra = new HashMap<>();
        extra.put("l2", Collections.singleton("quiz"));
        Map<String, java.util.Set<String>> skip = new HashMap<>();
        skip.put("l1", Collections.singleton("chat"));
        ServerGroup group = new ServerGroup("lobby", new HashSet<>(Arrays.asList("l1", "l2")),
            new HashSet<>(Arrays.asList("shop", "chat")), extra, skip);
        RemoteMenuData remote = connected(answers("groups", Collections.singletonList(group), "editGroup", true));

        ServerGroup back = remote.groups().get(0);
        assertEquals(Collections.singleton("shop"), back.wanted("l1"));
        assertEquals(new HashSet<>(Arrays.asList("shop", "chat", "quiz")), back.wanted("l2"));
        assertTrue(remote.editGroup(new GroupEdit(GroupOp.SKIP, "lobby", "l2", "chat")));
    }

    @Test
    void aCommandRunsOnTheOtherServerAndItsAnswerIsPrintedToTheViewer() {
        RemoteMenuData remote = connected(new HashMap<>());

        List<Component> lines = remote.command(new ViewerInfo("Admin", Collections.singleton("pnlibrary.admin")), "pn events full g1");

        assertEquals(Collections.singletonList(Component.text("printed pn events full g1")), lines);
    }

    @Test
    void dangerousActionsNeedAPermissionThatThisServerConfirmsNotOneTheRequestClaims() throws Exception {
        final UpdateActor actor = new UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin");
        final RemoteMenuData denied = connected(answers("rollback", true), (viewer, permission) -> false);
        Exception error = assertThrows(Exception.class,
            () -> denied.rollback(UUID.randomUUID(), false, actor).toCompletableFuture().get());
        assertTrue(mentions(error, "pnlibrary.updates.rollback"));

        RemoteMenuData granted = connected(answers("rollback", true),
            (viewer, permission) -> viewer.equals("Admin") && permission.equals("pnlibrary.updates.rollback"));
        assertTrue(granted.rollback(UUID.randomUUID(), false, actor).toCompletableFuture().get());
    }

    private static boolean mentions(Throwable error, String text) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void settingsTravelThroughTheLinkAndAChangeNeedsTheSettingsPermission() {
        final UpdateActor actor = new UpdateActor(UpdateActor.Kind.PLAYER, "id", "Admin");
        SettingRow row = new SettingRow("privacy", "Приватность отчёта", "SWITCH", "Прятать данные.", "false", "false", false, "",
            Collections.emptyList(), false);
        SettingRow zone = new SettingRow("time-zone", "Часовой пояс", "CHOICE", "Пояс.", "UTC", "UTC", false, "",
            Collections.singletonList("UTC"), true, "zones");
        RemoteMenuData granted = connected(answers("settings", Arrays.asList(row, zone),
            "changeSetting", new SettingOutcome(true, "true", false)),
            (viewer, permission) -> viewer.equals("Admin") && permission.equals("pnlibrary.settings"));
        assertEquals(Arrays.asList(row, zone), granted.settings());
        assertEquals(new SettingOutcome(true, "true", false), granted.changeSetting("privacy", "true", actor));

        final RemoteMenuData denied = connected(answers("changeSetting", new SettingOutcome(true, "true")), (viewer, permission) -> false);
        Exception error = assertThrows(Exception.class, () -> denied.changeSetting("privacy", "true", actor));
        assertTrue(mentions(error, "pnlibrary.settings"));
    }
}
