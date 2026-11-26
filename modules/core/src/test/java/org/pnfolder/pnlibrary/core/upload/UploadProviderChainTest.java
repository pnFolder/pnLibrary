package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class UploadProviderChainTest {
    @Test
    void usesProvidersInOrderUntilOneSucceeds() throws Exception {
        final List<String> calls = new ArrayList<>();
        final UploadReceipt expected = receipt("secondary");
        UploadProviderChain chain = new UploadProviderChain(Arrays.asList(
            provider("primary", calls, () -> {
                throw new IOException("offline");
            }),
            provider("secondary", calls, () -> expected)));

        UploadReceipt result = chain.upload("payload");

        assertSame(expected, result);
        assertEquals(Arrays.asList("primary", "secondary"), calls);
    }

    @Test
    void routesDeletionOnlyToTheReceiptBackend() throws Exception {
        final boolean[] called = {false, false};
        UploadProvider primary = deletingProvider("primary", () -> called[0] = true);
        UploadProvider secondary = deletingProvider("secondary", () -> called[1] = true);
        UploadProviderChain chain = new UploadProviderChain(Arrays.asList(primary, secondary));

        assertTrue(chain.delete(receipt("secondary")));
        assertFalse(called[0]);
        assertTrue(called[1]);
    }

    private static UploadProvider provider(final String id, final List<String> calls, final Callable<UploadReceipt> operation) {
        return new UploadProvider() {
            @Override
            public @NotNull String getBackendId() {
                return id;
            }

            @Override
            public @NotNull UploadReceipt upload(@NotNull String payload) throws IOException {
                calls.add(id);
                try {
                    return operation.call();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static UploadProvider deletingProvider(final String id, final Runnable markCalled) {
        return new UploadProvider() {
            @Override
            public @NotNull String getBackendId() {
                return id;
            }

            @Override
            public @NotNull UploadReceipt upload(@NotNull String payload) {
                return receipt(id);
            }

            @Override
            public boolean delete(@NotNull UploadReceipt receipt) {
                markCalled.run();
                return true;
            }
        };
    }

    private static UploadReceipt receipt(String backend) {
        return new UploadReceipt(URI.create("https://example.com/report"), "report", "token", 1, 0, backend);
    }
}
