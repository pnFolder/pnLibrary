package org.pnfolder.pnlibrary.updates.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;

class UpdatePreferencesStoreTest {
    @TempDir
    Path directory;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC);

    private Path file() {
        return directory.resolve("updates/preferences.json");
    }

    @Test
    @DisplayName("everything is saved in one file and read back")
    void everythingIsSavedInOneFileAndReadBack() {
        UpdatePreferencesStore store = new UpdatePreferencesStore(file());
        store.update(it -> it.withChannels(Collections.singletonMap("shop", "BETA"))
            .withAutomatic(Collections.singletonMap("shop", true)).withAllPlugins(false)
            .withFreezes(Collections.singletonMap("shop", 5L)));

        UpdatePreferencesStore.Preferences reopened = new UpdatePreferencesStore(file()).current();
        assertEquals(Collections.singletonMap("shop", "BETA"), reopened.getChannels());
        assertEquals(Collections.singletonMap("shop", true), reopened.getAutomatic());
        assertEquals(Boolean.FALSE, reopened.getAllPlugins());
        assertEquals(Collections.singletonMap("shop", 5L), reopened.getFreezes());
    }

    @Test
    @DisplayName("the choices and the pauses keep each others sections")
    void theChoicesAndThePausesKeepEachOthersSections() {
        UpdatePreferencesStore store = new UpdatePreferencesStore(file());
        UpdatePolicyOverrides overrides = new UpdatePolicyOverrides(store);
        FreezeStore freezes = new FreezeStore(store, clock);
        ProductId shop = ProductId.of("shop");

        freezes.freeze(shop, Duration.ofHours(2));
        overrides.setChannel("shop", UpdateChannel.RC);
        overrides.setAutomatic("shop", true);
        freezes.freeze(ProductId.of("auth"), Duration.ofHours(1));

        UpdatePreferencesStore reopened = new UpdatePreferencesStore(file());
        assertEquals(UpdateChannel.RC, new UpdatePolicyOverrides(reopened).channel("shop"));
        assertEquals(Boolean.TRUE, new UpdatePolicyOverrides(reopened).automatic("shop"));
        assertEquals(new java.util.HashSet<ProductId>(java.util.Arrays.asList(shop, ProductId.of("auth"))),
            new FreezeStore(reopened, clock).active().keySet());
    }

    @Test
    @DisplayName("concurrent saves from both owners are all kept")
    void concurrentSavesFromBothOwnersAreAllKept() throws Exception {
        UpdatePreferencesStore store = new UpdatePreferencesStore(file());
        UpdatePolicyOverrides overrides = new UpdatePolicyOverrides(store);
        FreezeStore freezes = new FreezeStore(store, clock);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        int rounds = 40;
        try {
            Future<Object> first = workers.submit((Callable<Object>) () -> {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    overrides.setAutomatic("p" + i, true);
                }
                return null;
            });
            Future<Object> second = workers.submit((Callable<Object>) () -> {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    freezes.freeze(ProductId.of("c" + i), Duration.ofHours(1));
                }
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        UpdatePreferencesStore reopened = new UpdatePreferencesStore(file());
        assertEquals(rounds, reopened.current().getAutomatic().size());
        assertEquals(rounds, new FreezeStore(reopened, clock).active().size());
    }

    @Test
    @DisplayName("an unreadable file is reported and replaced by the next save")
    void anUnreadableFileIsReportedAndReplacedByTheNextSave() throws Exception {
        Files.createDirectories(file().getParent());
        Files.write(file(), "{ not json".getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();
        UpdatePreferencesStore store = new UpdatePreferencesStore(file(), warnings::add);

        assertEquals(new UpdatePreferencesStore.Preferences(), store.current());
        assertEquals(1, warnings.size());
        store.update(it -> it.withAllPlugins(true));
        assertEquals(Boolean.TRUE, new UpdatePreferencesStore(file()).current().getAllPlugins());
    }

    @Test
    @DisplayName("nothing is written until something changes")
    void nothingIsWrittenUntilSomethingChanges() {
        UpdatePreferencesStore store = new UpdatePreferencesStore(file());
        store.update(it -> it);
        assertFalse(Files.exists(file()));
        store.update(it -> it.withAllPlugins(true));
        assertTrue(Files.exists(file()));
    }
}
