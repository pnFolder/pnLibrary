package org.pnfolder.pnlibrary.api.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.audience.delivery.DeliveryStatus;
import org.pnfolder.pnlibrary.api.audience.delivery.Outcome;
import org.pnfolder.pnlibrary.api.audience.delivery.Receipt;

class DeliveryTest {
    private final IllegalStateException cause = new IllegalStateException("boom");

    @Test
    @DisplayName("the status sums up the receipts")
    void theStatusSumsUpTheReceipts() {
        Delivery ok = Delivery.delivered();
        Delivery no = Delivery.unsupported();
        Delivery bad = Delivery.failed(cause);

        assertEquals(DeliveryStatus.DELIVERED, Delivery.combine(Arrays.asList(ok, ok)).getStatus());
        assertEquals(DeliveryStatus.PARTIAL, Delivery.combine(Arrays.asList(ok, no)).getStatus());
        assertEquals(DeliveryStatus.PARTIAL, Delivery.combine(Arrays.asList(bad, ok)).getStatus());
        assertEquals(DeliveryStatus.FAILED, Delivery.combine(Arrays.asList(no, bad)).getStatus());
        assertEquals(DeliveryStatus.UNSUPPORTED, Delivery.combine(Arrays.asList(no, no)).getStatus());
        assertEquals(DeliveryStatus.UNSUPPORTED, Delivery.combine(Collections.emptyList()).getStatus());
    }

    @Test
    @DisplayName("a delivery counts receivers and keeps every failure")
    void aDeliveryCountsReceiversAndKeepsEveryFailure() {
        IllegalArgumentException other = new IllegalArgumentException("other");
        Delivery mixed = Delivery.combine(Arrays.asList(Delivery.delivered(), Delivery.failed(cause), Delivery.failed(other)));

        assertEquals(1, mixed.getDelivered());
        assertEquals(3, mixed.getReceipts().size());
        assertEquals(Arrays.asList(cause, other), mixed.getFailures());
        assertSame(cause, mixed.getFailure());
        assertNull(Delivery.delivered().getFailure());
    }

    @Test
    @DisplayName("only a full delivery counts as delivered")
    void onlyAFullDeliveryCountsAsDelivered() {
        assertTrue(Delivery.of(true).isDelivered());
        assertFalse(Delivery.of(false).isDelivered());
        assertFalse(Delivery.failed(cause).isDelivered());
        assertFalse(Delivery.combine(Arrays.asList(Delivery.delivered(), Delivery.unsupported())).isDelivered());
    }

    @Test
    @DisplayName("orThrow passes a delivery through and throws the failure otherwise")
    void orThrowPassesADeliveryThroughAndThrowsTheFailureOtherwise() {
        Delivery ok = Delivery.delivered();

        assertSame(ok, ok.orThrow());
        assertSame(cause, assertThrows(IllegalStateException.class, () -> Delivery.failed(cause).orThrow()).getCause());
        assertThrows(IllegalStateException.class, () -> Delivery.unsupported().orThrow());
    }

    @Test
    @DisplayName("an audience reports whether the operation got through")
    void anAudienceReportsWhetherTheOperationGotThrough() {
        List<String> seen = new ArrayList<>();
        Audience ok = receiver("ok", seen, null);
        Audience broken = receiver("broken", seen, new IllegalStateException("lost"));

        assertEquals(DeliveryStatus.DELIVERED,
            AudienceExtensions.deliver(ok, it -> it.sendMessage(Component.text("hi"))).getStatus());
        assertEquals("lost",
            AudienceExtensions.deliver(broken, it -> it.sendMessage(Component.text("hi"))).getFailure().getMessage());
    }

    @Test
    @DisplayName("a group asks every receiver and keeps each answer")
    void aGroupAsksEveryReceiverAndKeepsEachAnswer() {
        List<String> seen = new ArrayList<>();
        Audience group = Audience.audience(receiver("first", seen, null), receiver("broken", seen, cause),
            receiver("last", seen, null));

        Delivery sent = AudienceExtensions.deliver(group, it -> it.sendMessage(Component.text("hi")));

        assertEquals(DeliveryStatus.PARTIAL, sent.getStatus());
        assertEquals(Arrays.asList("first", "broken", "last"), seen);
        List<Outcome> outcomes = new ArrayList<>();
        List<String> receivers = new ArrayList<>();
        for (Receipt receipt : sent.getReceipts()) {
            outcomes.add(receipt.getOutcome());
            receivers.add(receipt.getReceiver());
        }
        assertEquals(Arrays.asList(Outcome.DELIVERED, Outcome.FAILED, Outcome.DELIVERED), outcomes);
        assertEquals(Arrays.asList("first", "broken", "last"), receivers);
    }

    @Test
    @DisplayName("a group with nobody in it is unsupported and fatal errors are not caught")
    void aGroupWithNobodyInItIsUnsupportedAndFatalErrorsAreNotCaught() {
        Audience nobody = Audience.audience(Collections.emptyList());

        assertEquals(DeliveryStatus.UNSUPPORTED,
            AudienceExtensions.deliver(nobody, it -> it.sendMessage(Component.text("hi"))).getStatus());
        Audience dying = receiver("dying", new ArrayList<>(), new OutOfMemoryError());
        assertThrows(OutOfMemoryError.class,
            () -> AudienceExtensions.deliver(dying, it -> it.sendMessage(Component.text("hi"))));
    }

    private static Audience receiver(String name, List<String> seen, Throwable failure) {
        return new Audience() {
            @Override
            public @NotNull Pointers pointers() {
                return AudienceExtensions.senderPointers(name, permission -> false);
            }

            @Override
            public void sendMessage(@NotNull Component message) {
                seen.add(name);
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
            }
        };
    }
}
