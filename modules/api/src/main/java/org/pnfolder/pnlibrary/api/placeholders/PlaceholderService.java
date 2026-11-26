package org.pnfolder.pnlibrary.api.placeholders;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.Visibility;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * The placeholders of one module: annotated classes in, text out. Everything registered here is released when the
 * module closes.
 *
 * <p>An address is {@code {group.branch.argument.field|format|format}}. Calls may come from any thread; answers finish
 * on the thread the method that gave the value chose.
 */
@ApiStatus.NonExtendable
public interface PlaceholderService extends AutoCloseable {
    /**
     * Registers the classes marked with {@code @Placeholders}. With {@code papi} set, they are also shown to
     * PlaceholderAPI as {@code %papi_group_branch...%}; without it nothing is published outside.
     */
    void registerPublished(@Nullable String papi, @NotNull Object... holders);

    /** Registers the classes marked with {@code @Placeholders} without showing them to PlaceholderAPI. */
    default void register(@NotNull Object... holders) {
        registerPublished(null, holders);
    }

    /**
     * Registers one placeholder without a class, for names that are known only while running (one per currency, one
     * per setting). {@code address} is the plain path, such as {@code shop.coins}. Close the returned handle to remove it.
     */
    @NotNull
    AutoCloseable value(@NotNull String address, @NotNull String description, @Nullable String papi,
                        @NotNull Visibility visibility, @NotNull PlaceholderValue resolve);

    /** Like the call with a {@link Visibility}, visible to everyone. */
    @NotNull
    default AutoCloseable value(@NotNull String address, @NotNull String description, @Nullable String papi,
                                @NotNull PlaceholderValue resolve) {
        return value(address, description, papi, Visibility.ALL, resolve);
    }

    /** Registers one placeholder without a class, visible to everyone and without PlaceholderAPI. */
    @NotNull
    default AutoCloseable value(@NotNull String address, @NotNull String description, @NotNull PlaceholderValue resolve) {
        return value(address, description, null, resolve);
    }

    /** Registers one placeholder without a class, with no description and without PlaceholderAPI. */
    @NotNull
    default AutoCloseable value(@NotNull String address, @NotNull PlaceholderValue resolve) {
        return value(address, "", null, resolve);
    }

    /** Registers the methods marked with {@code @Format} of {@code holders} as formats of this module: {@code {value|name}}. */
    void formats(@NotNull Object... holders);

    /**
     * Registers how text becomes a {@code type} for the arguments of this module: {@code @Argument} parameters of that
     * type use it. Close the returned handle to remove it; it goes away with the module too. A converter named on the
     * parameter ({@code @Argument(converter = ...)}) is used before this one.
     */
    @NotNull
    <T> AutoCloseable converter(@NotNull Class<T> type, @NotNull ArgumentConverter<T> converter);

    /**
     * The value of {@code address} (with or without the braces), or {@code null} when it is unknown or has no value. A
     * name in {@code values} wins over every registered placeholder: it is how a scenario or a command hands over its
     * own data.
     */
    @NotNull
    CompletionStage<Object> resolve(@NotNull String address, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values);

    /** The value of {@code address} for nobody in particular. */
    @NotNull
    default CompletionStage<Object> resolve(@NotNull String address) {
        return resolve(address, ViewerInfo.none(), Collections.<String, Object>emptyMap());
    }

    /** The value of {@code address} for {@code viewer}. */
    @NotNull
    default CompletionStage<Object> resolve(@NotNull String address, @NotNull ViewerInfo viewer) {
        return resolve(address, viewer, Collections.<String, Object>emptyMap());
    }

    /** Like the call with a {@link ViewerInfo}, for a plain player id (or {@code null}). */
    @NotNull
    default CompletionStage<Object> resolve(@NotNull String address, @Nullable UUID playerId) {
        return resolve(address, ViewerInfo.of(playerId), Collections.<String, Object>emptyMap());
    }

    /** Like the call with a {@link ViewerInfo}, for a plain player id (or {@code null}). */
    @NotNull
    default CompletionStage<Object> resolve(@NotNull String address, @Nullable UUID playerId, @NotNull Map<String, Object> values) {
        return resolve(address, ViewerInfo.of(playerId), values);
    }

    /** Replaces every {@code {address}} of {@code text}; an address that gives nothing stays as written. Then PlaceholderAPI fills its own {@code %...%}. */
    @NotNull
    CompletionStage<String> render(@NotNull String text, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values);

    /** {@code text} filled in for nobody in particular. */
    @NotNull
    default CompletionStage<String> render(@NotNull String text) {
        return render(text, ViewerInfo.none(), Collections.<String, Object>emptyMap());
    }

    /** {@code text} filled in for {@code viewer}. */
    @NotNull
    default CompletionStage<String> render(@NotNull String text, @NotNull ViewerInfo viewer) {
        return render(text, viewer, Collections.<String, Object>emptyMap());
    }

    /** Like the call with a {@link ViewerInfo}, for a plain player id (or {@code null}). */
    @NotNull
    default CompletionStage<String> render(@NotNull String text, @Nullable UUID playerId) {
        return render(text, ViewerInfo.of(playerId), Collections.<String, Object>emptyMap());
    }

    /** Like the call with a {@link ViewerInfo}, for a plain player id (or {@code null}). */
    @NotNull
    default CompletionStage<String> render(@NotNull String text, @Nullable UUID playerId, @NotNull Map<String, Object> values) {
        return render(text, ViewerInfo.of(playerId), values);
    }

    /** Whether {@code address} names a registered placeholder; nothing is evaluated. */
    boolean contains(@NotNull String address);

    /** Every placeholder of the whole library, for lists and menus. */
    @NotNull
    List<PlaceholderInfo> list();

    /** The same placeholders as {@link #list}, as a {@link Registry}: found by address, with owner and visibility. */
    @NotNull
    Registry<PlaceholderInfo> registry();

    /** Every format that {@code {value|name}} can use, with the module that brought it. */
    @NotNull
    Registry<FormatInfo> formatRegistry();

    /** The outside placeholder systems (PlaceholderAPI) the library talks to. */
    @NotNull
    PlaceholderAdapterRegistry adapters();

    /** Releases everything this module registered. */
    @Override
    void close();
}
