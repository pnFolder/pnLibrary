package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Rewrites a YAML object into the object the polymorphic codec expects, before its {@code type} is read.
 *
 * <p>Declare it with {@link ConfigNodeForm} on the polymorphic base type. It lets an object without a {@code type}
 * (a wrapper such as {@code delay: 2s} with a {@code do:} list) stand where an action is accepted.
 *
 * <p>Implementations return {@code node} unchanged when they do not recognise it.
 */
public interface ConfigNodeRewriter {
    /** Returns the object to decode in place of {@code node}; {@code path} is the location used in messages. */
    @NotNull
    Map<String, Object> rewrite(@NotNull Map<String, Object> node, @NotNull String path);
}
