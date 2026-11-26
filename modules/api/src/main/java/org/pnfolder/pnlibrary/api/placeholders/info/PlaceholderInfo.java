package org.pnfolder.pnlibrary.api.placeholders.info;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.Visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One placeholder as an administrator sees it in lists. */
public final class PlaceholderInfo {
    private final String address;
    private final String description;
    private final String owner;
    private final String type;
    private final String format;
    private final List<String> published;
    private final Visibility visibility;

    /** A placeholder without a format, not published, visible to all. */
    public PlaceholderInfo(@NotNull String address, @NotNull String description, @NotNull String owner, @NotNull String type) {
        this(address, description, owner, type, null);
    }

    /** A placeholder that is not published, visible to all. */
    public PlaceholderInfo(@NotNull String address, @NotNull String description, @NotNull String owner, @NotNull String type,
                           @Nullable String format) {
        this(address, description, owner, type, format, Collections.<String>emptyList());
    }

    /** A placeholder visible to all. */
    public PlaceholderInfo(@NotNull String address, @NotNull String description, @NotNull String owner, @NotNull String type,
                           @Nullable String format, @NotNull List<String> published) {
        this(address, description, owner, type, format, published, Visibility.ALL);
    }

    /**
     * Creates the description.
     *
     * @param address the address with its arguments, such as {@code clan.war.{arg1}.score}
     * @param description the text of {@code @Placeholder("...")}, or an empty string
     * @param owner the plugin and module that registered it, as {@code plugin/module}
     * @param type the type of the value
     * @param format the format applied when the address names none, or {@code null}
     * @param published the names under which PlaceholderAPI shows it, such as {@code pnclans_clan_level}
     * @param visibility who may use it
     */
    public PlaceholderInfo(@NotNull String address, @NotNull String description, @NotNull String owner, @NotNull String type,
                           @Nullable String format, @NotNull List<String> published, @NotNull Visibility visibility) {
        this.address = Objects.requireNonNull(address);
        this.description = Objects.requireNonNull(description);
        this.owner = Objects.requireNonNull(owner);
        this.type = Objects.requireNonNull(type);
        this.format = format;
        this.published = Collections.unmodifiableList(new ArrayList<>(published));
        this.visibility = Objects.requireNonNull(visibility);
    }

    /** The address with its arguments. */
    @NotNull
    public String getAddress() {
        return address;
    }

    /** The description, or an empty string. */
    @NotNull
    public String getDescription() {
        return description;
    }

    /** The plugin and module that registered it, as {@code plugin/module}. */
    @NotNull
    public String getOwner() {
        return owner;
    }

    /** The type of the value. */
    @NotNull
    public String getType() {
        return type;
    }

    /** The format applied when the address names none, or {@code null}. */
    @Nullable
    public String getFormat() {
        return format;
    }

    /** The names under which PlaceholderAPI shows it. */
    @NotNull
    public List<String> getPublished() {
        return published;
    }

    /** Who may use it. */
    @NotNull
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlaceholderInfo)) {
            return false;
        }
        PlaceholderInfo that = (PlaceholderInfo) other;
        return address.equals(that.address) && description.equals(that.description) && owner.equals(that.owner)
                && type.equals(that.type) && Objects.equals(format, that.format) && published.equals(that.published)
                && visibility == that.visibility;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, description, owner, type, format, published, visibility);
    }

    @NotNull
    @Override
    public String toString() {
        return "PlaceholderInfo(address=" + address + ", owner=" + owner + ", type=" + type + ", format=" + format
                + ", published=" + published + ", visibility=" + visibility + ")";
    }
}
