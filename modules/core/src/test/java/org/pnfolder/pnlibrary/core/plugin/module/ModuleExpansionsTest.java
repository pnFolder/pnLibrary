package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion;
import org.pnfolder.pnlibrary.api.expansions.Expansion;
import org.pnfolder.pnlibrary.api.expansions.ExpansionState;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.core.expansions.ExpansionRegistry;
import org.pnfolder.pnlibrary.core.expansions.ModuleBinding;
import org.pnfolder.pnlibrary.core.expansions.ModuleBoundExpansion;
import org.pnfolder.pnlibrary.core.plugin.LoadedPlugins;
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture;

class ModuleExpansionsTest {
    private final ExpansionRegistry expansions = new ExpansionRegistry((expansion, error) -> {
        throw new AssertionError(error);
    });
    private final ExpansionRegistry library = new ExpansionRegistry((expansion, error) -> {
        throw new AssertionError(error);
    });
    private final LoadedPlugins registry = PluginRegistryFixture.builder().expansions(expansions).libraryExpansions(library).build();
    private final Object owner = new Object();
    private final Bound bound = new Bound();

    @BeforeEach
    void start() {
        library.register(bound);
    }

    @AfterEach
    void stop() {
        expansions.close();
        library.close();
    }

    private ModuleContext module(String id) {
        return PluginRegistryFixture.registerModule(registry, owner, id, it -> { });
    }

    private static String view(ModuleContext context) {
        return ((ModuleExpansions) context.getExpansions()).view(Bound.class);
    }

    @Test
    void aViewIsBoundOncePerModule() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");

        assertSame(view(alpha), view(alpha));
        assertEquals(1, bound.bindings.size());
        assertNotEquals(view(alpha), view(beta));
        assertEquals(2, bound.bindings.size());
    }

    @Test
    void whatAViewOwnsClosesWithItsModuleAndNotWithAnother() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        AtomicBoolean ofAlpha = new AtomicBoolean();
        AtomicBoolean ofBeta = new AtomicBoolean();
        view(alpha);
        view(beta);
        bound.bindings.get(0).own(() -> new Flag(ofAlpha));
        bound.bindings.get(1).own(() -> new Flag(ofBeta));

        alpha.close();

        assertTrue(ofAlpha.get());
        assertFalse(ofBeta.get());
        boolean[] acquired = {false};
        assertThrows(IllegalStateException.class, () -> bound.bindings.get(0).own(() -> {
            acquired[0] = true;
            return new Flag(new AtomicBoolean());
        }));
        assertFalse(acquired[0]);
    }

    @Test
    void anExpansionAModuleRegistersEndsWithTheModule() throws Exception {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        Plain weather = new Plain("weather");
        AutoCloseable handle = alpha.getExpansions().register(weather);
        Plain stays = new Plain("stays");
        beta.getExpansions().register(stays);

        assertEquals(ExpansionState.ENABLED, weather.getState());
        assertSame(weather, beta.getExpansions().get("weather"));

        alpha.close();

        assertEquals(ExpansionState.DISABLED, weather.getState());
        assertNull(expansions.get("weather"));
        assertEquals(ExpansionState.ENABLED, stays.getState());
        handle.close();
        assertSame(stays, expansions.get("stays"));
    }

    @Test
    void theHandleRemovesAnExpansionEarlyAndItsIdCanBeUsedAgain() throws Exception {
        ModuleContext alpha = module("alpha");
        Plain first = new Plain("weather");
        AutoCloseable handle = alpha.getExpansions().register(first);

        handle.close();
        handle.close();
        assertEquals(ExpansionState.DISABLED, first.getState());
        assertNull(expansions.get("weather"));

        Plain second = new Plain("weather");
        alpha.getExpansions().register(second);
        assertSame(second, expansions.get("weather"));
    }

    @Test
    void expansionIdsAreUniqueAcrossModulesAndALibraryExpansionIsNeitherListedNorShadowed() {
        ModuleContext alpha = module("alpha");
        ModuleContext beta = module("beta");
        alpha.getExpansions().register(new Plain("weather"));

        assertThrows(IllegalStateException.class, () -> beta.getExpansions().register(new Plain("weather")));
        assertThrows(IllegalStateException.class, () -> alpha.getExpansions().register(new Plain("weather")));

        assertNull(beta.getExpansions().get("bound"));
        List<String> ids = new ArrayList<>();
        for (Expansion expansion : beta.getExpansions().all()) {
            ids.add(expansion.getId());
        }
        assertEquals(Arrays.asList("weather"), ids);
        beta.getExpansions().register(new Plain("bound"));
        assertSame(bound, library.get("bound"));
        assertEquals("view-1", view(beta));
    }

    @Test
    void aClosedModuleCanNeitherRegisterNorBind() {
        ModuleContext alpha = module("alpha");
        alpha.close();

        assertThrows(IllegalStateException.class, () -> alpha.getExpansions().register(new Plain("late")));
        assertThrows(IllegalStateException.class, () -> view(alpha));
    }

    @Test
    void aViewOfAnExpansionTheRuntimeLacksSaysSo() {
        ModuleContext bare = PluginRegistryFixture.registerModule(PluginRegistryFixture.registry(), owner, "alpha", it -> { });

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> view(bare));
        assertEquals("Bound is not available on this platform", failure.getMessage());
    }

    private static final class Plain extends AbstractExpansion {
        private Plain(String id) {
            super(id);
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }
    }

    private static final class Bound extends AbstractExpansion implements ModuleBoundExpansion<String> {
        private final List<ModuleBinding> bindings = new ArrayList<>();

        private Bound() {
            super("bound");
        }

        @Override
        public @NotNull String bind(@NotNull ModuleBinding module) {
            bindings.add(module);
            return "view-" + bindings.size();
        }

        @Override
        protected void onEnable() {
        }

        @Override
        protected void onDisable() {
        }
    }

    private static final class Flag implements AutoCloseable {
        private final AtomicBoolean closed;

        private Flag(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
