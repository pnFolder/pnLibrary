package org.pnfolder.pnlibrary.core.expansions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion;
import org.pnfolder.pnlibrary.api.expansions.Expansion;
import org.pnfolder.pnlibrary.api.expansions.ExpansionState;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;

class ExpansionRegistryTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final ExpansionRegistry manager =
        new ExpansionRegistry((expansion, error) -> failures.add(expansion.getId() + ": " + error.getMessage()));

    private final class Probe extends AbstractExpansion {
        private final String id;
        private final boolean failOnEnable;
        private final boolean failOnDisable;

        Probe(String id) {
            this(id, false, false);
        }

        Probe(String id, boolean failOnEnable, boolean failOnDisable) {
            super(id);
            this.id = id;
            this.failOnEnable = failOnEnable;
            this.failOnDisable = failOnDisable;
        }

        @Override
        protected void onEnable() {
            if (failOnEnable) {
                throw new IllegalStateException("enable failed");
            }
            events.add("enable " + id);
        }

        @Override
        protected void onDisable() {
            events.add("disable " + id);
            if (failOnDisable) {
                throw new IllegalStateException("disable failed");
            }
        }
    }

    @Test
    void registeringEnablesTheExpansionOnce() {
        Probe probe = new Probe("a");

        manager.register(probe);
        probe.enable();

        assertEquals(ExpansionState.ENABLED, probe.getState());
        assertEquals(Collections.singletonList("enable a"), events);
        assertSame(probe, manager.get("a"));
        assertSame(probe, manager.get(Probe.class));
    }

    @Test
    void aDuplicateIdIsRejectedAndTheFirstExpansionIsUntouched() {
        Probe first = new Probe("a");
        manager.register(first);

        assertThrows(IllegalStateException.class, () -> manager.register(new Probe("a")));

        assertSame(first, manager.get("a"));
        assertEquals(ExpansionState.ENABLED, first.getState());
    }

    @Test
    void closingTheHandleUnregistersAndDisablesOnce() throws Exception {
        Probe probe = new Probe("a");
        AutoCloseable handle = manager.register(probe);

        handle.close();
        handle.close();

        assertNull(manager.get("a"));
        assertEquals(ExpansionState.DISABLED, probe.getState());
        assertEquals(Arrays.asList("enable a", "disable a"), events);
    }

    @Test
    void aFailingExpansionIsReportedAndDoesNotStopTheOthers() {
        Probe broken = new Probe("broken", true, false);
        Probe healthy = new Probe("healthy");

        manager.register(broken);
        manager.register(healthy);

        assertEquals(ExpansionState.FAILED, broken.getState());
        assertEquals(ExpansionState.ENABLED, healthy.getState());
        assertEquals(Collections.singletonList("broken: enable failed"), failures);
        assertNotNull(manager.get("broken"));
    }

    @Test
    void closingStopsExpansionsInReverseOrderEvenWhenOneFails() {
        Probe first = new Probe("a");
        Probe second = new Probe("b", false, true);
        Probe third = new Probe("c");
        for (Probe probe : Arrays.asList(first, second, third)) {
            manager.register(probe);
        }
        events.clear();

        assertThrows(IllegalStateException.class, manager::close);

        assertEquals(Arrays.asList("disable c", "disable b", "disable a"), events);
        for (Probe probe : Arrays.asList(first, second, third)) {
            assertEquals(ExpansionState.CLOSED, probe.getState());
        }
        assertThrows(IllegalStateException.class, () -> manager.register(new Probe("d")));
    }

    @Test
    void theRegistryListsExpansionsByIdAndOwner() {
        manager.register(new Probe("localization"));

        List<RegistryEntry<Expansion>> entries = manager.registry().entries();

        assertEquals(1, entries.size());
        assertEquals("localization", entries.get(0).getKey());
        assertEquals("pnLibrary", entries.get(0).getOwner());
        assertNotNull(manager.registry().get("localization"));
    }

    @Test
    void requireFindsARegisteredExpansionAndNamesTheRegisteredOnesWhenItIsMissing() {
        manager.register(new Probe("localization"));

        assertNotNull(manager.require("localization"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> manager.require("missing"));
        assertEquals("Expansion 'missing' is not registered; registered: localization", error.getMessage());
    }
}
