package org.pnfolder.pnlibrary.core.link.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.UpdateSettings;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

class RemoteExecutorTest {
    private static final class Call {
        final String product;
        final boolean enabled;
        final UpdateActor actor;

        Call(String product, boolean enabled, UpdateActor actor) {
            this.product = product;
            this.enabled = enabled;
            this.actor = actor;
        }
    }

    private final List<Call> calls = new ArrayList<Call>();
    private CompletionStage<UpdateSnapshot> automaticAnswer = CompletableFuture.completedFuture(null);
    private final UpdateSettings settings = mock(UpdateSettings.class, call -> {
        if (call.getMethod().getName().equals("automaticDownload")) {
            calls.add(new Call(call.<String>getArgument(0), call.<Boolean>getArgument(1), call.<UpdateActor>getArgument(2)));
            return automaticAnswer;
        }
        return null;
    });
    private final UpdateProducts products = mock(UpdateProducts.class);
    private final UpdateService updates = mock(UpdateService.class);
    private final PnLibrary library = mock(PnLibrary.class);
    private Instant now = Instant.parse("2026-11-04T10:00:00Z");
    private final RemoteExecutor executor;
    private final List<RemoteResult> results = new ArrayList<RemoteResult>();

    RemoteExecutorTest() {
        when(updates.getSettings()).thenReturn(settings);
        when(updates.getProducts()).thenReturn(products);
        when(library.getUpdates()).thenReturn(updates);
        executor = new RemoteExecutor(library, () -> now);
    }

    private RemoteRequest request(UUID id, RemoteAction action) {
        return new RemoteRequest(id, "lobby", "proxy", "Admin", action, "shop", now);
    }

    private RemoteRequest request(RemoteAction action) {
        return request(UUID.randomUUID(), action);
    }

    private void run(RemoteRequest request) {
        executor.run(request, results::add);
    }

    @Test
    void automaticDownloadIsSwitchedThroughTheUpdateServiceForARemoteActor() {
        run(request(RemoteAction.AUTO_ON));

        Call call = calls.get(0);
        assertEquals("shop", call.product);
        assertTrue(call.enabled);
        assertEquals(UpdateActor.Kind.INTEGRATION, call.actor.getKind());
        assertEquals("remote:proxy", call.actor.getId());
        assertTrue(results.get(0).getOk());
        assertTrue(results.get(0).getText().contains("включена"));
    }

    @Test
    void aFailureOfTheUpdateServiceBecomesARefusalWithItsReason() {
        CompletableFuture<UpdateSnapshot> failed = new CompletableFuture<UpdateSnapshot>();
        failed.completeExceptionally(new IllegalStateException("диск полон"));
        automaticAnswer = failed;

        run(request(RemoteAction.AUTO_OFF));

        assertFalse(results.get(0).getOk());
        assertTrue(results.get(0).getText().contains("диск полон"));
    }

    @Test
    void anActionThatThrowsIsLoggedAsAnErrorAndRefused() {
        PnLogger logger = mock(PnLogger.class);
        LoggingService logging = mock(LoggingService.class);
        Object owner = new Object();
        when(logging.logger(owner, "link")).thenReturn(logger);
        when(library.getLogging()).thenReturn(logging);
        when(library.getOwner()).thenReturn(owner);
        IllegalStateException failure = new IllegalStateException("update service is down");
        when(products.get("shop")).thenThrow(failure);

        run(request(RemoteAction.UPDATE));

        verify(logger).error(ArgumentMatchers.anyString(), ArgumentMatchers.eq(failure));
        assertFalse(results.get(0).getOk());
    }

    @Test
    void aRequestThatWasAlreadyCarriedOutIsRefused() {
        RemoteRequest same = request(RemoteAction.AUTO_ON);

        run(same);
        run(same);

        assertEquals(Arrays.asList(true, false), Arrays.asList(results.get(0).getOk(), results.get(1).getOk()));
        assertTrue(results.get(1).getText().contains("уже выполнялся"));
        assertEquals(1, calls.size());
    }

    @Test
    void anOldRequestIdIsForgottenAfterTenMinutes() {
        RemoteRequest same = request(RemoteAction.AUTO_ON);

        run(same);
        now = now.plus(Duration.ofMinutes(11));
        run(same);

        assertEquals(Arrays.asList(true, true), Arrays.asList(results.get(0).getOk(), results.get(1).getOk()));
    }

    @Test
    void updatingAPluginTheLibraryDoesNotKnowIsRefused() {
        run(request(RemoteAction.UPDATE));

        assertFalse(results.get(0).getOk());
        assertTrue(results.get(0).getText().contains("не подключён"));
    }
}
