package org.pnfolder.pnlibrary.api.libraries;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleLibraryTest {
    @Test
    void sourcesAreTriedInTheGivenOrderAndMavenCentralLast() {
        ModuleLibrary library = ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
                .source(s -> s.repository("https://one.example/maven/")).source(s -> s.link("https://files.example/a.jar")).build();

        assertEquals(Arrays.asList("https://one.example/maven/", "https://files.example/a.jar", ModuleLibrary.CENTRAL), urls(library));
        List<Boolean> direct = new ArrayList<>();
        for (LibrarySource source : library.getAllSources()) {
            direct.add(source.isDirect());
        }
        assertEquals(Arrays.asList(false, true, false), direct);
        assertEquals("com.zaxxer:HikariCP:5.1.0", library.getCoordinates());
        assertEquals(LibraryScope.PLUGIN, library.getScope());
        assertTrue(library.isRequired());
        assertEquals(Collections.singletonList(ModuleLibrary.CENTRAL), urls(ModuleLibrary.of("g:a:1")));
    }

    @Test
    void mavenCentralCanBeTurnedOff() {
        ModuleLibrary own = ModuleLibrary.builder("g", "a", "1").source(s -> s.repository("https://one.example/")).mavenCentral(false).build();
        assertEquals(Collections.singletonList("https://one.example/"), urls(own));
    }

    @Test
    void settingsAreCheckedWhenTheLibraryIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> ModuleLibrary.builder("g", "a", "1").source(s -> s.repository("http://plain.example/")));
        assertThrows(IllegalArgumentException.class, () -> ModuleLibrary.builder("g", "a", "1").source(s -> s.link("https://x.example/a.jar").sha256("abc")));
        assertThrows(IllegalArgumentException.class, () -> ModuleLibrary.builder("g /x", "a", "1").build());
        assertThrows(IllegalArgumentException.class, () -> ModuleLibrary.of("only:two"));
        assertThrows(IllegalStateException.class, () -> LibrarySource.builder().build());
    }

    @Test
    void scopeAndOptionalChangeHowTheLibraryIsLoaded() {
        ModuleLibrary library = ModuleLibrary.builder("g", "a", "1").scope(LibraryScope.ISOLATED).optional().build();

        assertTrue(library.isIsolated());
        assertFalse(library.isRequired());
        assertEquals("g:a:1:shaded", ModuleLibrary.of("g:a:1:shaded").getCoordinates());
    }

    @Test
    void everySourceHasAnAddressASumAndANameOfItsOwn() {
        String one = repeat('A', 64);
        ModuleLibrary library = ModuleLibrary.builder("g", "a", "1")
                .source(s -> s.link("https://cdn.example/a.jar").sha256(one).name("CDN").description("  Быстрый  "))
                .source(s -> s.repository("https://mirror.example/")).build();

        List<LibrarySource> all = library.getAllSources();
        assertEquals(repeat('a', 64), all.get(0).getSha256());
        assertEquals("CDN", all.get(0).getName());
        assertEquals("Быстрый", all.get(0).getDescription());
        assertNull(all.get(1).getDescription());
        assertTrue(all.get(0).isDirect());
        assertEquals("mirror.example", all.get(1).getName());
        assertNull(all.get(1).getSha256());
    }

    @Test
    void aLibraryCanBeLimitedToSomePlatforms() {
        ModuleLibrary any = ModuleLibrary.of("g:a:1");
        ModuleLibrary bukkit = ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.BUKKIT).build();

        assertTrue(any.appliesTo(PlatformType.VELOCITY));
        assertTrue(bukkit.appliesTo(PlatformType.BUKKIT));
        assertFalse(bukkit.appliesTo(PlatformType.VELOCITY));
        assertTrue(bukkit.appliesTo(null));
        assertThrows(IllegalArgumentException.class, () -> ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.UNIVERSAL));
    }

    @Test
    void aLibraryHasAReadableTitleAndDescription() {
        ModuleLibrary plain = ModuleLibrary.of("com.zaxxer:HikariCP:5.1.0");
        ModuleLibrary named = ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
                .title("Пул соединений").description("  Держит открытые соединения с базой.  ").build();

        assertEquals("HikariCP", plain.getTitle());
        assertNull(plain.getDescription());
        assertEquals("Пул соединений", named.getTitle());
        assertEquals("Держит открытые соединения с базой.", named.getDescription());
    }

    private static List<String> urls(ModuleLibrary library) {
        List<String> urls = new ArrayList<>();
        for (LibrarySource source : library.getAllSources()) {
            urls.add(source.getUrl());
        }
        return urls;
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }
}
