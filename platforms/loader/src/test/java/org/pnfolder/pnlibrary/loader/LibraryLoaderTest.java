package org.pnfolder.pnlibrary.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Checks repository order, retries and optional library groups of {@link LibraryLoader}. */
class LibraryLoaderTest {
    // Kotlin is not on this module's classpath, which is exactly the light JAR's situation.
    private static final ClassLoader LOADER = LibraryLoaderTest.class.getClassLoader();
    private static final Library LIBRARY = Library.builder().groupId("g").artifactId("a").version("1").build();

    @Test
    void triesRepositoriesInOrderOwnThenCentralThenFallback() {
        LibraryManager manager = mock(LibraryManager.class);
        LibrarySettings settings = LibrarySettings.builder()
                .repository("https://one.example/").repository("https://two.example/")
                .fallback("https://mirror.example/").build();

        LibraryLoader.load(manager, LOADER, settings, LIBRARY);

        InOrder order = inOrder(manager);
        order.verify(manager).addRepository("https://one.example/");
        order.verify(manager).addRepository("https://two.example/");
        order.verify(manager).addMavenCentral();
        order.verify(manager).addRepository("https://mirror.example/");
        order.verify(manager).loadLibrary(LIBRARY);
    }

    @Test
    void skipsMavenCentralWhenDisabled() {
        LibraryManager manager = mock(LibraryManager.class);
        LibrarySettings settings = LibrarySettings.builder()
                .repository("https://only.example/").mavenCentral(false).build();

        LibraryLoader.load(manager, LOADER, settings, LIBRARY);

        verify(manager, never()).addMavenCentral();
        verify(manager).addRepository("https://only.example/");
    }

    @Test
    void retriesAFailedDownloadUntilItSucceeds() {
        LibraryManager manager = mock(LibraryManager.class);
        doThrow(new RuntimeException("slow")).doThrow(new RuntimeException("slow")).doNothing()
                .when(manager).loadLibrary(any(Library.class));

        LibraryLoader.load(manager, LOADER, LibrarySettings.builder().attempts(3).build(), LIBRARY);

        verify(manager, times(3)).loadLibrary(LIBRARY);
    }

    @Test
    void failsOnceEveryAttemptIsUsed() {
        LibraryManager manager = mock(LibraryManager.class);
        RuntimeException failure = new RuntimeException("unreachable");
        doThrow(failure).when(manager).loadLibrary(any(Library.class));

        LibraryLoadException thrown = assertThrows(LibraryLoadException.class,
                () -> LibraryLoader.load(manager, LOADER, LibrarySettings.builder().attempts(2).build(), LIBRARY));

        assertSame(failure, thrown.getCause());
        assertEquals("g:a:1", thrown.library());
        assertEquals(2, thrown.attempts());
        verify(manager, times(2)).loadLibrary(LIBRARY);
    }

    @Test
    void loadsEveryLibraryInTheGivenOrder() {
        LibraryManager manager = mock(LibraryManager.class);
        Library second = Library.builder().groupId("g").artifactId("b").version("1").build();
        doNothing().when(manager).loadLibrary(any(Library.class));

        LibraryLoader.load(manager, LOADER, LibrarySettings.DEFAULT, LIBRARY, second);

        InOrder order = inOrder(manager);
        order.verify(manager).loadLibrary(LIBRARY);
        order.verify(manager).loadLibrary(second);
    }

    @Test
    void defaultSettingsUseCentralAndKeepTheFallbackMirror() {
        LibrarySettings settings = LibrarySettings.DEFAULT;

        assertEquals(3, settings.attempts());
        assertEquals(true, settings.mavenCentral());
        assertEquals(1, settings.fallbacks().size());
    }

    @Test
    void librariesGoToTheServerFolderByDefault() {
        Path data = Paths.get("srv", "plugins", "pnLibrary");

        String expected = Paths.get("srv", "libraries", "pnlibrary").toAbsolutePath().toString();
        assertEquals(expected, LibrarySettings.DEFAULT.directoryFor(data));
    }

    @Test
    void librariesCanStayInThePluginFolder() {
        Path data = Paths.get("srv", "plugins", "pnLibrary");
        LibrarySettings settings = LibrarySettings.builder().pluginDirectory("lib").build();

        assertEquals(data.resolve("lib").toAbsolutePath().toString(), settings.directoryFor(data));
    }

    @Test
    void settingsRejectNonsense() {
        assertThrows(IllegalArgumentException.class, () -> LibrarySettings.builder().serverDirectory("../up"));
        assertThrows(IllegalArgumentException.class, () -> LibrarySettings.builder().pluginDirectory("/abs"));
        assertThrows(IllegalArgumentException.class, () -> LibrarySettings.builder().repository("ftp://x"));
        assertThrows(IllegalArgumentException.class, () -> LibrarySettings.builder().attempts(0));
        assertThrows(IllegalArgumentException.class, () -> LibrarySettings.builder().serverDirectory(" "));
        assertThrows(IllegalStateException.class, () -> LibrarySettings.builder().mavenCentral(false).build());
    }

    @Test
    void newJavaGetsTheNewCompilerAndOldJavaTheOldOne() {
        assertSame(RuntimeLibraries.COMPILER_MODERN, LibraryLoader.librariesFor("compiler", 21));
        assertSame(RuntimeLibraries.COMPILER_LEGACY, LibraryLoader.librariesFor("compiler", 11));
    }

    @Test
    void optionalGroupsAreLoadedOnlyWhenAsked() {
        LibraryManager manager = mock(LibraryManager.class);
        LibraryLoader.load(manager, LOADER, LibrarySettings.DEFAULT, LIBRARY);
        verify(manager, never()).loadLibrary(RuntimeLibraries.DRIVER_MYSQL[0]);

        assertEquals(true, LibraryLoader.loadOptional("mysql"));
        verify(manager).loadLibrary(RuntimeLibraries.DRIVER_MYSQL[0]);
        assertEquals(false, LibraryLoader.loadOptional("nothing"));
    }

    @Test
    void anOptionalLibraryThatCannotBeLoadedDoesNotThrow() {
        LibraryManager manager = mock(LibraryManager.class);
        LibraryLoader.load(manager, LOADER, LibrarySettings.DEFAULT, LIBRARY);
        doThrow(new RuntimeException("offline")).when(manager).loadLibrary(any(Library.class));

        assertEquals(false, LibraryLoader.loadOptional("compiler"));
    }

    @Test
    void optionalLoadsFromTwoThreadsNeverOverlap() throws Exception {
        LibraryManager manager = mock(LibraryManager.class);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        doAnswer(call -> {
            if (running.incrementAndGet() > 1) overlaps.incrementAndGet();
            Thread.sleep(50);
            running.decrementAndGet();
            return null;
        }).when(manager).loadLibrary(any(Library.class));
        LibraryLoader.load(manager, LOADER, LibrarySettings.DEFAULT, LIBRARY);

        Thread first = new Thread(() -> LibraryLoader.loadOptional("mysql"));
        Thread second = new Thread(() -> LibraryLoader.loadOptional("postgres"));
        first.start();
        second.start();
        first.join();
        second.join();

        assertEquals(0, overlaps.get());
    }
}
