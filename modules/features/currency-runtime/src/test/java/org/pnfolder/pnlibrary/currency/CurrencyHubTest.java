package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationState;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class CurrencyHubTest {
    @Test
    void localAliasResolvesForTheOwnerAndTheCurrencyIsVisibleToAnotherPlugin() {
        CurrencyHub hub = new CurrencyHub();
        CurrencyService owner = hub.scope(PluginId.of("owner"), placeholders());
        CurrencyService consumer = hub.scope(PluginId.of("consumer"), placeholders());
        CurrencyRegistration registration = owner.register("coins", provider(), options -> options.aliases("money"));

        assertSame(registration, owner.get("money"));
        assertSame(registration, consumer.get("owner:coins"));
        hub.close();
    }

    @Test
    void sharedRegistrationFollowsDisableEnableAndScopeCloseLifecycle() throws Exception {
        CurrencyHub hub = new CurrencyHub();
        CurrencyService owner = hub.scope(PluginId.of("owner"), placeholders());
        CurrencyService consumer = hub.scope(PluginId.of("consumer"), placeholders());
        CurrencyRegistration registration = owner.register("coins", provider(), options -> { });

        assertSame(registration, consumer.get("owner:coins"));
        registration.disable();
        assertEquals(CurrencyRegistrationState.DISABLED, registration.getState());
        assertNull(consumer.get("owner:coins"));
        registration.enable();
        assertSame(registration, consumer.get("owner:coins"));

        owner.close();
        assertEquals(CurrencyRegistrationState.CLOSED, registration.getState());
        assertNull(hub.get("owner:coins"));
        assertThrows(IllegalStateException.class, registration::enable);
        hub.close();
    }

    private static final class BrokenProvider implements CurrencyProvider, AutoCloseable {
        @Override
        public CurrencyDescriptor getDescriptor() {
            return new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
        }

        @Override
        public CompletableFuture<BigDecimal> balance(@NotNull CurrencyAccount account) {
            throw new IllegalStateException("balance broke");
        }

        @Override
        public void close() {
            throw new IllegalStateException("close broke");
        }
    }

    @Test
    void aProviderThatThrowsIsReportedToTheOwnerAsWellAsFailingTheCall() throws Exception {
        List<Throwable> reported = new ArrayList<>();
        CurrencyHub hub = new CurrencyHub((message, error) -> reported.add(error));
        CurrencyService owner = hub.scope(PluginId.of("owner"), placeholders());
        CurrencyRegistration registration = owner.register("coins", new BrokenProvider());

        assertThrows(Exception.class, () -> registration.balance(UUID.randomUUID()).toCompletableFuture().get());
        registration.close();

        List<String> messages = new ArrayList<>();
        for (Throwable error : reported) {
            messages.add(error.getMessage());
        }
        assertEquals(Arrays.asList("balance broke", "close broke"), messages);
        hub.close();
    }

    private static CurrencyProvider provider() {
        return new ClosableProvider();
    }

    private static final class ClosableProvider implements CurrencyProvider, AutoCloseable {
        @Override
        public CurrencyDescriptor getDescriptor() {
            return new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
        }

        @Override
        public CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }

        @Override
        public void close() {
        }
    }

    private static PlaceholderService placeholders() {
        return (PlaceholderService) Proxy.newProxyInstance(PlaceholderService.class.getClassLoader(),
                new Class<?>[] {PlaceholderService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new IllegalStateException("Unexpected placeholder call: " + method.getName());
                });
    }
}
