package org.pnfolder.pnlibrary.core.menu.data.question;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.menu.owner.OwnerInfo;

/** What an event is for, how to use it, and which plugin owns it. */
public final class EventAbout {
    private final String purpose;
    private final String usage;
    private final OwnerInfo owner;

    public EventAbout(@NotNull String purpose, @NotNull String usage, @Nullable OwnerInfo owner) {
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.owner = owner;
    }

    public @NotNull String getPurpose() {
        return purpose;
    }

    public @NotNull String getUsage() {
        return usage;
    }

    public @Nullable OwnerInfo getOwner() {
        return owner;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EventAbout)) {
            return false;
        }
        EventAbout that = (EventAbout) other;
        return purpose.equals(that.purpose) && usage.equals(that.usage) && Objects.equals(owner, that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purpose, usage, owner);
    }

    @Override
    public String toString() {
        return "EventAbout(purpose=" + purpose + ", usage=" + usage + ", owner=" + owner + ")";
    }
}
