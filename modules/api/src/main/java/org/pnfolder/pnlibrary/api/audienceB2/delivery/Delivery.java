package org.pnfolder.pnlibrary.api.audienceB2.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audienceB2.AudienceExtensions;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * What became of one attempt to show something (a message, a sound, an effect, a command) to a receiver or a whole
 * audience. Adventure's methods return nothing, so a lost message is invisible; a delivery says who got it and why not.
 *
 * <pre>{@code
 * Delivery sent = Delivery.to(admins, it -> it.sendMessage(Component.text("Shop reloaded")));
 * switch (sent.getStatus()) {
 *     case PARTIAL: logger.warning(sent.getDelivered() + " of " + sent.getReceipts().size() + " admins got it"); break;
 *     case UNSUPPORTED: logger.info("No admin is online"); break;
 *     case FAILED: throw new IllegalStateException(sent.getFailure());
 *     default: break;
 * }
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface Delivery {
    /** One answer per receiver, in the order they were asked; empty when the audience had nobody. */
    @NotNull List<Receipt> getReceipts();

    /** The summary of {@link #getReceipts()}. */
    @NotNull DeliveryStatus getStatus();

    /** How many receivers got it. */
    int getDelivered();

    /** The first failure reported, or {@code null}. */
    @Nullable Throwable getFailure();

    /** Returns this delivery when every receiver got it; otherwise throws, with the first failure as the cause. */
    @NotNull Delivery orThrow();

    /** The platform accepted the request. */
    static @NotNull Delivery delivered() {
        return DeliveryReport.DELIVERED;
    }

    /** The platform has no way to do this. */
    static @NotNull Delivery unsupported() {
        return DeliveryReport.UNSUPPORTED;
    }

    /** The platform could do this, but the attempt failed with {@code cause}. */
    static @NotNull Delivery failed(@NotNull Throwable cause) {
        return new DeliveryReport(Collections.singletonList(new Receipt(null, DeliveryStatus.FAILED, cause)));
    }

    /** Joins several deliveries into one that keeps every receipt. */
    static @NotNull Delivery combine(@NotNull Iterable<Delivery> deliveries) {
        List<Delivery> all = new ArrayList<Delivery>();
        for (Delivery delivery : deliveries) {
            all.add(delivery);
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        List<Receipt> receipts = new ArrayList<Receipt>();
        for (Delivery delivery : all) {
            receipts.addAll(delivery.getReceipts());
        }
        return new DeliveryReport(receipts);
    }

    /**
     * Runs {@code operation} on {@code audience}, receiver by receiver. A receiver that throws gets a failed receipt
     * and the others still get the operation; fatal errors propagate.
     */
    static @NotNull Delivery to(@NotNull Audience audience, @NotNull Consumer<Audience> operation) {
        if (audience instanceof ForwardingAudience) {
            List<Delivery> each = new ArrayList<Delivery>();
            for (Audience member : ((ForwardingAudience) audience).audiences()) {
                each.add(to(member, operation));
            }
            return combine(each);
        }
        String name = AudienceExtensions.name(audience);
        try {
            operation.accept(audience);
            return new DeliveryReport(Collections.singletonList(new Receipt(name, DeliveryStatus.DELIVERED)));
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            return new DeliveryReport(Collections.singletonList(new Receipt(name, DeliveryStatus.FAILED, error)));
        }
    }
}
