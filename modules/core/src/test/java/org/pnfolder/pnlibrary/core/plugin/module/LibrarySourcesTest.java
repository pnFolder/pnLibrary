package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.library.SourceHealth;
import org.pnfolder.pnlibrary.core.plugin.module.library.ModuleLibraryLoader;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.libraries.LibrarySource;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

class LibrarySourcesTest {
    @Test
    void aLibraryForAnotherPlatformIsSkippedWithoutAnError() {
        AtomicInteger fetched = new AtomicInteger();
        ModuleLibraryLoader libraries = new ModuleLibraryLoader(null, PlatformType.VELOCITY, library -> {
            fetched.incrementAndGet();
            return null;
        });
        libraries.load(ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.BUKKIT).build());
        libraries.load(ModuleLibrary.builder("g", "b", "1").platforms(PlatformType.VELOCITY, PlatformType.BUNGEECORD).build());

        assertEquals(1, fetched.get());
        List<String> loaded = new ArrayList<>();
        for (ModuleLibrary library : libraries.getLoaded()) {
            loaded.add(library.getCoordinates());
        }
        assertEquals(Arrays.asList("g:b:1"), loaded);
    }

    @Test
    void anyNumberOfSourcesAreTriedInTheGivenOrder() {
        ModuleLibrary library = ModuleLibrary.builder("g", "a", "1")
            .source(it -> it.link("https://one.example/a.jar")).source(it -> it.link("https://two.example/a.jar"))
            .source(it -> it.repository("https://repo1.example/")).source(it -> it.repository("https://repo2.example/"))
            .mavenCentral(false).build();
        assertEquals(
            Arrays.asList("https://one.example/a.jar", "https://two.example/a.jar", "https://repo1.example/", "https://repo2.example/"),
            urls(library.getAllSources()));
    }

    @Test
    void aThousandAlternativesAreAccepted() {
        ModuleLibrary.Builder builder = ModuleLibrary.builder("g", "a", "1").mavenCentral(false);
        for (int index = 0; index < 1000; index++) {
            final int number = index;
            builder.source(it -> it.repository("https://m" + number + ".example/"));
        }
        assertEquals(1000, builder.build().getAllSources().size());
    }

    @Test
    void aPlaceThatFailedAMomentAgoIsTriedLastAndForgottenAfterFiveMinutes() {
        SourceHealth.forgetAll();
        ModuleLibrary library = ModuleLibrary.builder("g", "a", "1")
            .source(it -> it.repository("https://one.example/")).source(it -> it.repository("https://two.example/"))
            .mavenCentral(false).build();
        SourceHealth.failed("https://one.example/", 1_000);

        assertEquals(Arrays.asList("https://two.example/", "https://one.example/"),
            urls(SourceHealth.order(library.getAllSources(), 2_000)));
        assertEquals(Arrays.asList("https://one.example/", "https://two.example/"),
            urls(SourceHealth.order(library.getAllSources(), 400_000)));
        SourceHealth.forgetAll();
    }

    private static List<String> urls(List<LibrarySource> sources) {
        List<String> urls = new ArrayList<>();
        for (LibrarySource source : sources) {
            urls.add(source.getUrl());
        }
        return urls;
    }
}
