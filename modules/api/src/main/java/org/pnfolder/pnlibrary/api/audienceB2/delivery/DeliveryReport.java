package org.pnfolder.pnlibrary.api.audienceB2.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DeliveryReport implements Delivery {
    static final DeliveryReport DELIVERED = new DeliveryReport(
        Collections.singletonList(new Receipt(null, DeliveryStatus.DELIVERED)));
    static final DeliveryReport UNSUPPORTED = new DeliveryReport(
        Collections.singletonList(new Receipt(null, DeliveryStatus.UNSUPPORTED)));

    private final List<Receipt> receipts;
    private final DeliveryStatus status;

    DeliveryReport(List<Receipt> receipts) {
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
            boolean delivered = receipt.getStatus() == DeliveryStatus.DELIVERED;
            all &= delivered;
            any |= delivered;
            failed |= receipt.getStatus() == DeliveryStatus.FAILED;
        }
        if (all) {
            return DeliveryStatus.DELIVERED;
        }
        if (any) {
            return DeliveryStatus.PARTIAL;
        }
        return failed ? DeliveryStatus.FAILED : DeliveryStatus.UNSUPPORTED;
    }

    @Override
    public List<Receipt> getReceipts() {
        return receipts;
    }

    @Override
    public DeliveryStatus getStatus() {
        return status;
    }

    @Override
    public int getDelivered() {
        int count = 0;
        for (Receipt receipt : receipts) {
            if (receipt.getStatus() == DeliveryStatus.DELIVERED) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Throwable getFailure() {
        for (Receipt receipt : receipts) {
            if (receipt.getCause() != null) {
                return receipt.getCause();
            }
        }
        return null;
    }

    @Override
    public Delivery orThrow() {
        if (status != DeliveryStatus.DELIVERED) {
            throw new IllegalStateException(
                "Delivery " + status + ": " + getDelivered() + " of " + receipts.size() + " receivers got it", getFailure());
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DeliveryReport && ((DeliveryReport) other).receipts.equals(receipts);
    }

    @Override
    public int hashCode() {
        return receipts.hashCode();
    }

    @Override
    public String toString() {
        return "Delivery(" + status + ", " + getDelivered() + " of " + receipts.size() + ")";
    }
}
