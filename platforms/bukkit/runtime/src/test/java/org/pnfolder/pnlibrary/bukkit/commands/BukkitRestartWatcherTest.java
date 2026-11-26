package org.pnfolder.pnlibrary.bukkit.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;

class BukkitRestartWatcherTest {
    private final PnLibrary library = mock(PnLibrary.class, RETURNS_DEEP_STUBS);
    private final ServerRestarter restarter = mock(ServerRestarter.class);
    private final BukkitRestartFlow flow = mock(BukkitRestartFlow.class);
    private final PnLogger logger = mock(PnLogger.class);
    private final Supplier<PnLogger> log = () -> logger;
    private RestartSettings settings = new RestartSettings();
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private BukkitRestartWatcher watcher(Instant started) {
        return new BukkitRestartWatcher(library, restarter, flow, () -> settings, log, started);
    }

    private BukkitRestartWatcher watcher() {
        return watcher(Instant.now().minus(Duration.ofHours(1)));
    }

    private UpdatePlanSnapshot plan(UpdateState state) {
        UpdatePlanSnapshot plan = new UpdatePlanSnapshot(UUID.randomUUID(), 1, state, null, Collections.emptyList(), null);
        when(library.getUpdates().getPlans().current()).thenReturn(Optional.of(plan));
        return plan;
    }

    @Test
    void aRestartThatHappenedEarlierLeavesNothingToWaitFor() {
        BukkitRestartWatcher watcher = watcher();
        UpdatePlanSnapshot staged = plan(UpdateState.UPDATE_STAGED);
        watcher.check();
        verify(flow, times(1)).announce(any(), isNull());

        // Any restart, by hand or alone, applies the plan: it is gone and the watcher has nothing to do or to break.
        when(library.getUpdates().getPlans().current()).thenReturn(Optional.empty());
        watcher.check();
        watcher.check();

        verify(flow, times(1)).announce(any(), any());
        verify(restarter, never()).start(any());
        assertFalse(watcher.suppressAuto());
        assertEquals(UpdateState.UPDATE_STAGED, staged.getState());
    }

    @Test
    void asksAdministratorsOncePerPlan() {
        plan(UpdateState.UPDATE_STAGED);
        BukkitRestartWatcher watcher = watcher();

        watcher.check();
        watcher.check();

        verify(flow, times(1)).announce(any(), isNull());
        verify(restarter, never()).start(any());
    }

    @Test
    void restartsAloneWhenTheRuleAllowsAndRecordsItAsASystemAction() {
        settings = new RestartSettings(true, 2, Duration.ZERO);
        plan(UpdateState.UPDATE_STAGED);
        BukkitRestartWatcher watcher = watcher();

        watcher.check();
        watcher.check();

        verify(restarter, times(1)).start(ServerRestarter.Mode.NOW);
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("system"));
    }

    @Test
    void doesNotRestartInTheFirstTenMinutesAfterStart() {
        settings = new RestartSettings(true, 0, Duration.ZERO);
        plan(UpdateState.UPDATE_STAGED);

        watcher(Instant.now().minusSeconds(60)).check();

        verify(restarter, never()).start(any());
    }

    @Test
    void afterARollbackItAsksAndNeverRestartsAlone() {
        settings = new RestartSettings(true, 0, Duration.ZERO);
        BukkitRestartWatcher watcher = watcher();
        plan(UpdateState.UPDATE_STAGED);
        watcher.check();
        plan(UpdateState.ROLLED_BACK);

        watcher.check();

        verify(flow, times(1)).announce(any(), anyString());
    }

    @Test
    void aRollbackThatWasThereAtStartIsNotAskedAboutAgain() {
        plan(UpdateState.ROLLED_BACK);

        watcher().check();

        verify(flow, never()).announce(any(), any());
    }

    @Test
    void cancelWithdrawsTheAutomaticRestartOfTheWaitingPlan() {
        settings = new RestartSettings(true, 0, Duration.ZERO);
        plan(UpdateState.UPDATE_STAGED);
        BukkitRestartWatcher watcher = watcher();

        assertTrue(watcher.suppressAuto());
        assertFalse(watcher.suppressAuto());
        watcher.check();

        verify(restarter, never()).start(any());
    }
}
