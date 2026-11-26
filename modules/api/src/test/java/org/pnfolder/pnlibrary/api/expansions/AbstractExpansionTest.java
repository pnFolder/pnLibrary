package org.pnfolder.pnlibrary.api.expansions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractExpansionTest {
    private static final class Probe extends AbstractExpansion {
        final List<String> calls = new ArrayList<>();
        boolean failEnable;
        boolean failDisable;

        Probe() {
            super("probe");
        }

        @Override
        protected void onEnable() {
            calls.add("enable");
            if (failEnable) {
                throw new IllegalStateException("no enable");
            }
        }

        @Override
        protected void onDisable() {
            calls.add("disable");
            if (failDisable) {
                throw new IllegalStateException("no disable");
            }
        }

        @Override
        protected void onClose() {
            calls.add("close");
        }
    }

    @Test
    void enableAndDisableMoveTheStateAndCallHooksOnce() {
        Probe probe = new Probe();
        probe.enable();
        probe.enable();
        assertEquals(ExpansionState.ENABLED, probe.getState());
        probe.disable();
        probe.disable();

        assertEquals(ExpansionState.DISABLED, probe.getState());
        assertEquals(Arrays.asList("enable", "disable"), probe.calls);
    }

    @Test
    void aFailedEnableMarksTheExpansionFailedAndMayBeRetried() {
        Probe probe = new Probe();
        probe.failEnable = true;
        assertThrows(IllegalStateException.class, probe::enable);
        assertEquals(ExpansionState.FAILED, probe.getState());

        probe.failEnable = false;
        probe.enable();
        assertEquals(ExpansionState.ENABLED, probe.getState());
    }

    @Test
    void closeReleasesAnEnabledExpansionThenClosesItForGood() {
        Probe probe = new Probe();
        probe.enable();
        probe.close();
        probe.close();
        probe.enable();

        assertEquals(ExpansionState.CLOSED, probe.getState());
        assertEquals(Arrays.asList("enable", "disable", "close"), probe.calls);
    }

    @Test
    void closingADisabledExpansionSkipsOnDisable() {
        Probe probe = new Probe();
        probe.close();

        assertEquals(Arrays.asList("close"), probe.calls);
    }

    @Test
    void aFailingOnDisableStillEndsInTheTargetStateAndCloses() {
        Probe probe = new Probe();
        probe.failDisable = true;
        probe.enable();
        assertThrows(IllegalStateException.class, probe::close);

        assertEquals(ExpansionState.CLOSED, probe.getState());
        assertEquals(Arrays.asList("enable", "disable", "close"), probe.calls);
    }
}
