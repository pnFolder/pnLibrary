package org.pnfolder.pnlibrary.api.audience.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * What became of one attempt to present something (a message, a sound, an effect, a command) to one receiver or a
 * whole audience. Adventure's own methods return nothing, so a lost message is invisible; a delivery says who got
 * it, who could not, and why.
 *
 * <pre>{@code
 * Delivery sent = AudienceExtensions.deliver(admins, it -> it.sendMessage(Component.text("Shop reloaded")));
 * switch (sent.getStatus()) {
 *     case PARTIAL: logger.warning("Only " + sent.getDelivered() + " of " + sent.getReceipts().size() + " admins got it"); break;
 *     case UNSUPPORTED: logger.info("No admin is online"); break;
 *     case FAILED: throw new IllegalStateException(sent.getFailure());
 *     default: break;
 * }
 * }</pre>
 *
 * <p>A group is asked receiver by receiver: each one gets the operation even when another fails, and every answer is
 * kept in {@link #getReceipts()}. Players answer for their own effects, particles and commands through the same type.
 */
public final class Delivery {
    private static final Delivery DELIVERED = new Delivery(Collections.singletonList(new Receipt(null, Outcome.DELIVERED)));
    private static final Delivery UNSUPPORTED = new Delivery(Collections.singletonList(new Receipt(null, Outcome.UNSUPPORTED)));

    private final List<Receipt> receipts;
    private final DeliveryStatus status;

    private Delivery(List<Receipt> receipts) {
        this.receipts = Collections.unmodifiableList(new ArrayList<Receipt>(receipts));
        this.status = summarize(this.receipts);
    }

    private static DeliveryStatus summarize(List<Receipt> receipts) {
        if (receipts.isEmpty()) {
            return DeliveryStatus.UNSUPPORTED;
        }
        boolean all = true;
        boolean any = false;
        boolean failed = false;
        for (Receipt receipt : receipts) {
            boolean delivered = receipt.getOutcome() == Outcome.DELIVERED;
            all &= delivered;
            any |= delivered;
            failed |= receipt.getOutcome() == Outcome.FAILED;
        }
        if (all) {
            return DeliveryStatus.DELIVERED;
        }
        if (any) {
            return DeliveryStatus.PARTIAL;
        }
        return failed ? DeliveryStatus.FAILED : DeliveryStatus.UNSUPPORTED;
    }

    /** One entry per receiver, in the order they were asked; empty when the audience had nobody. */
    public @NotNull List<Receipt> getReceipts() {
        return receipts;
    }

    /** The summary of {@link #getReceipts()}. */
    public @NotNull DeliveryStatus getStatus() {
        return status;
    }

    /** {@code true} only when every receiver got it, which is {@link DeliveryStatus#DELIVERED}. */
    public boolean isDelivered() {
        return status == DeliveryStatus.DELIVERED;
    }

    /** How many receivers got it. */
    public int getDelivered() {
        int count = 0;
        for (Receipt receipt : receipts) {
            if (receipt.getOutcome() == Outcome.DELIVERED) {
                count++;
            }
        }
        return count;
    }

    /** Every failure the platform reported, in order. */
    public @NotNull List<Throwable> getFailures() {
        List<Throwable> failures = new ArrayList<Throwable>();
        for (Receipt receipt : receipts) {
            if (receipt.getCause() != null) {
                failures.add(receipt.getCause());
            }
        }
        return failures;
    }

    /** The first failure the platform reported, or {@code null} when there was none. */
    public @Nullable Throwable getFailure() {
        List<Throwable> failures = getFailures();
        return failures.isEmpty() ? null : failures.get(0);
    }

    /** Returns this delivery when every receiver got it, and throws otherwise: the first failure is the cause. */
    public @NotNull Delivery orThrow() {
        if (!isDelivered()) {
            throw new IllegalStateException(
                "Delivery " + status + ": " + getDelivered() + " of " + receipts.size() + " receivers got it", getFailure());
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Delivery && ((Delivery) other).receipts.equals(receipts);
    }

    @Override
    public int hashCode() {
        return receipts.hashCode();
    }

    @Override
    public String toString() {
        return "Delivery(" + status + ", " + getDelivered() + " of " + receipts.size() + ")";
    }

    /** The platform accepted the request. */
    public static @NotNull Delivery delivered() {
        return DELIVERED;
    }

    /** The platform has no way to do this. */
    public static @NotNull Delivery unsupported() {
        return UNSUPPORTED;
    }

    /** The platform could do this, but the attempt failed with {@code cause}. */
    public static @NotNull Delivery failed(@NotNull Throwable cause) {
        return new Delivery(Collections.singletonList(new Receipt(null, Outcome.FAILED, cause)));
    }

    /** Maps a platform answer that only says accepted or not to {@link #delivered()} or {@link #unsupported()}. */
    public static @NotNull Delivery of(boolean accepted) {
        return accepted ? DELIVERED : UNSUPPORTED;
    }

    /** Joins several deliveries into one that keeps every receipt. */
    public static @NotNull Delivery combine(@NotNull Iterable<Delivery> deliveries) {
        List<Delivery> all = new ArrayList<Delivery>();
        for (Delivery delivery : deliveries) {
            all.add(delivery);
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        List<Receipt> receipts = new ArrayList<Receipt>();
        for (Delivery delivery : all) {
            receipts.addAll(delivery.receipts);
        }
        return new Delivery(receipts);
    }

    /**
     * Runs {@code operation} on {@code audience} and reports it. A group is asked receiver by receiver; an error
     * thrown by one receiver becomes its {@link Outcome#FAILED} receipt, except fatal errors, which propagate.
     */
    public static @NotNull Delivery to(@NotNull Audience audience, @NotNull Consumer<Audience> operation) {
        if (audience instanceof ForwardingAudience) {
            List<Delivery> each = new ArrayList<Delivery>();
            for (Audience member : ((ForwardingAudience) audience).audiences()) {
                each.add(to(member, operation));
            }
            return combine(each);
        }
        try {
            operation.accept(audience);
            return new Delivery(Collections.singletonList(new Receipt(AudienceExtensions.name(audience), Outcome.DELIVERED)));
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            return new Delivery(Collections.singletonList(new Receipt(AudienceExtensions.name(audience), Outcome.FAILED, error)));
        }
    }
}
