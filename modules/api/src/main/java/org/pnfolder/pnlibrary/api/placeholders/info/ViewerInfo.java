package org.pnfolder.pnlibrary.api.placeholders.info;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.NoValueException;

import java.util.UUID;

/** Who asks for the text. Add a parameter of this type to a placeholder method and the library fills it in. */
public final class ViewerInfo {
    /** The language used when none is known. */
    public static final String DEFAULT_LANGUAGE = "en";

    private final UUID playerId;
    private final String language;
    private final boolean viaPapi;

    /** A viewer for {@code playerId} in the default language. */
    public ViewerInfo(@Nullable UUID playerId) {
        this(playerId, DEFAULT_LANGUAGE);
    }

    /** A viewer that did not come through PlaceholderAPI. */
    public ViewerInfo(@Nullable UUID playerId, @NotNull String language) {
        this(playerId, language, false);
    }

    /**
     * Creates a viewer.
     *
     * @param playerId the player the text is for, or {@code null} for the console and background work
     * @param language the language of the player
     * @param viaPapi whether the request came through PlaceholderAPI
     */
    public ViewerInfo(@Nullable UUID playerId, @NotNull String language, boolean viaPapi) {
        this.playerId = playerId;
        this.language = Objects.requireNonNull(language, "language");
        this.viaPapi = viaPapi;
    }

    /** A viewer for {@code playerId}. */
    @NotNull
    public static ViewerInfo of(@Nullable UUID playerId) {
        return new ViewerInfo(playerId);
    }

    /** A viewer without a player. */
    @NotNull
    public static ViewerInfo none() {
        return new ViewerInfo(null);
    }

    /** The player the text is for, or {@code null} for the console and background work. */
    @Nullable
    public UUID getPlayerId() {
        return playerId;
    }

    /** The language of the player. */
    @NotNull
    public String getLanguage() {
        return language;
    }

    /** Whether the request came through PlaceholderAPI. */
    public boolean isViaPapi() {
        return viaPapi;
    }

    /** Whether there is a player. */
    public boolean hasPlayer() {
        return playerId != null;
    }

    /** The player, or ends the placeholder quietly (it shows nothing) when there is none. */
    @NotNull
    public UUID requirePlayerId() {
        if (playerId == null) {
            throw new NoValueException("This placeholder needs a player");
        }
        return playerId;
    }

    @NotNull
    @Override
    public String toString() {
        return "ViewerInfo(player=" + playerId + ", language=" + language + ", papi=" + viaPapi + ")";
    }
}
