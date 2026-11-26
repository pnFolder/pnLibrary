package org.pnfolder.pnlibrary.core.expansions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.expansions.ExpansionState;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.MinecraftLocale;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationException;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;

class LocalizationExpansionTest {
    @TempDir
    Path cache;

    private LocalizationExpansion expansion;
    private MinecraftLocalization view;

    private LocalizationExpansion expansion() {
        if (expansion == null) {
            expansion = new LocalizationExpansion(cache);
        }
        return expansion;
    }

    private MinecraftLocalization view() {
        if (view == null) {
            view = expansion().bind(new Unbound());
        }
        return view;
    }

    /** Reaches the live loader without touching the network: an unknown version is rejected before any download. */
    private TranslationException.Reason liveReason() {
        ExecutionException failure = assertThrows(ExecutionException.class,
            () -> view().availableLocales(MinecraftVersion.UNKNOWN).toCompletableFuture().get());
        return ((TranslationException) failure.getCause()).getReason();
    }

    private TranslationException.Reason closedReasonOf(Runnable call) {
        return assertThrows(TranslationException.class, call::run).getReason();
    }

    @Test
    void startingDownloadsAndStoresNothing() throws IOException {
        expansion().enable();

        assertEquals(ExpansionState.ENABLED, expansion().getState());
        try (Stream<Path> files = Files.walk(cache)) {
            assertTrue(files.noneMatch(Files::isRegularFile));
        }
    }

    @Test
    void aPluginClosingItsViewDoesNotStopTheLoader() {
        expansion().enable();

        view().close();
        view().getLayouts().close();

        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, liveReason());
        assertEquals(ExpansionState.ENABLED, expansion().getState());
    }

    @Test
    void disablingClosesTheLoaderAndEnablingRestoresIt() {
        expansion().enable();
        expansion().disable();

        assertEquals(TranslationException.Reason.CLOSED, closedReasonOf(() -> view().availableLocales(MinecraftVersion.V1_20_4)));

        expansion().enable();
        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, liveReason());
    }

    @Test
    void registeredLayoutsSurviveDisablingAndAreDroppedWhenTheExpansionCloses() {
        KeyboardLayout layout = KeyboardLayout.of("expansion-test", "abcdefghijklmnopqrstuvwxyz012345678");
        expansion().enable();
        view().getLayouts().register(layout);

        expansion().disable();
        expansion().enable();
        assertTrue(view().getLayouts().all().contains(layout));

        expansion().close();
        assertEquals(KeyboardLayout.builtIn, view().getLayouts().all());
    }

    @Test
    void layoutsAViewRegistersBelongToItsModule() {
        KeyboardLayout layout = KeyboardLayout.of("expansion-owned", "abcdefghijklmnopqrstuvwxyz012345678");
        final List<AutoCloseable> owned = new ArrayList<>();
        ModuleBinding module = new ModuleBinding() {
            @Override
            public <R extends AutoCloseable> @NotNull R own(@NotNull Supplier<R> acquire) {
                R resource = acquire.get();
                owned.add(resource);
                return resource;
            }
        };
        expansion().enable();

        expansion().bind(module).getLayouts().register(layout);

        assertEquals(1, owned.size());
        assertTrue(view().getLayouts().all().contains(layout));
        try {
            owned.get(0).close();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        assertEquals(KeyboardLayout.builtIn, view().getLayouts().all());
    }

    @Test
    void defaultLookupsGoThroughTheView() {
        assertEquals(TranslationException.Reason.CLOSED,
            closedReasonOf(() -> view().locale(MinecraftVersion.V1_20_4, MinecraftLocale.RU_RU)));
    }

    /** A binding whose module never closes. */
    private static final class Unbound implements ModuleBinding {
        @Override
        public <R extends AutoCloseable> @NotNull R own(@NotNull Supplier<R> acquire) {
            return acquire.get();
        }
    }
}
