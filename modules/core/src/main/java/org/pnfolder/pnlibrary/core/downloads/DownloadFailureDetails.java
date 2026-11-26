package org.pnfolder.pnlibrary.core.downloads;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Administrator-facing explanation of a failed download and what the user and the developer can do about it. */
public final class DownloadFailureDetails {
    private final String headline;
    private final String reason;
    private final String userAction;
    private final String developerAction;

    /** Creates the explanation. */
    public DownloadFailureDetails(@NotNull String headline, @NotNull String reason, @NotNull String userAction,
                                  @NotNull String developerAction) {
        this.headline = Objects.requireNonNull(headline, "headline");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.userAction = Objects.requireNonNull(userAction, "userAction");
        this.developerAction = Objects.requireNonNull(developerAction, "developerAction");
    }

    /** One line saying what went wrong. */
    public @NotNull String getHeadline() {
        return headline;
    }

    /** Why it went wrong. */
    public @NotNull String getReason() {
        return reason;
    }

    /** What the server owner can do. */
    public @NotNull String getUserAction() {
        return userAction;
    }

    /** What to tell the plugin developer. */
    public @NotNull String getDeveloperAction() {
        return developerAction;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DownloadFailureDetails)) {
            return false;
        }
        DownloadFailureDetails that = (DownloadFailureDetails) other;
        return headline.equals(that.headline) && reason.equals(that.reason)
            && userAction.equals(that.userAction) && developerAction.equals(that.developerAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headline, reason, userAction, developerAction);
    }

    @Override
    public String toString() {
        return "DownloadFailureDetails(headline=" + headline + ", reason=" + reason + ", userAction=" + userAction
            + ", developerAction=" + developerAction + ")";
    }
}
