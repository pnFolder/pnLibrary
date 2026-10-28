package org.pnfolder.pnlibrary.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.List;
import net.byteflux.libby.Library;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

class StartupFailureTest {
    private static final Library LIBRARY =
            Library.builder().groupId("org.jetbrains.kotlin").artifactId("kotlin-stdlib").version("2.1.21").build();

    private static String render(Throwable error, LibrarySettings settings) {
        List<String> lines = StartupFailure.card(ConsoleTheme.plain(), settings,
                Paths.get("srv", "plugins", "pnLibrary"), error).render();
        return String.join("\n", lines);
    }

    @Test
    void aFailedDownloadNamesTheLibraryTheFolderAndWhereToAsk() {
        LibraryLoadException error = new LibraryLoadException(LIBRARY, 3, new RuntimeException("timed out"));

        String text = render(error, LibrarySettings.DEFAULT);

        assertTrue(text.contains("org.jetbrains.kotlin:kotlin-stdlib:2.1.21"), text);
        assertTrue(text.contains("timed out"), text);
        assertTrue(text.contains("попыток: 3"), text);
        assertTrue(text.contains("repo1.maven.org"), text);
        assertTrue(text.contains(Paths.get("srv", "libraries", "pnlibrary").toAbsolutePath().toString()), text);
        assertTrue(text.contains("universal-full"), text);
        assertTrue(text.contains(ProductInfo.SUPPORT_URL), text);
    }

    @Test
    void anyOtherErrorStillShowsTheReasonAndTheSupportLink() {
        String text = render(new IllegalStateException("boom"), LibrarySettings.DEFAULT);

        assertTrue(text.contains("IllegalStateException: boom"), text);
        assertTrue(text.contains("latest.log"), text);
        assertTrue(text.contains("NoClassDefFoundError: PnLibraryProvider"), text);
        assertTrue(text.contains(ProductInfo.SUPPORT_URL), text);
        assertFalse(text.contains("universal-full"), text);
    }

    @Test
    void reflectionWrappersAreSeenThrough() {
        Throwable wrapped = new InvocationTargetException(new IllegalStateException("real cause"));

        String text = render(wrapped, LibrarySettings.DEFAULT);

        assertTrue(text.contains("real cause"), text);
        assertFalse(text.contains("InvocationTargetException"), text);
    }

    @Test
    void ownRepositoriesAreListedFirst() {
        LibrarySettings settings = LibrarySettings.builder().repository("https://mine.example/maven/").build();
        LibraryLoadException error = new LibraryLoadException(LIBRARY, 1, new RuntimeException("404"));

        String text = render(error, settings);

        assertTrue(text.indexOf("mine.example") < text.indexOf("repo1.maven.org"), text);
    }
}
