package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Handle for one serializer that a plugin published for the files at some address. */
public interface ConfigSerializerRegistration extends AutoCloseable {
    /** Plugin that published the serializer. */
    @NotNull
    PluginId getOwner();

    /** The value type the serializer converts. */
    @NotNull
    Class<?> getType();

    /** Where the serializer works: everything, a plugin, a module or one file. */
    @NotNull
    ConfigAddress getTarget();

    /** The higher value wins when several additions handle one type. */
    int getPriority();

    /** Whether the serializer is still used by configuration codecs. */
    boolean isActive();

    /** Stops using the serializer. Closing is idempotent. */
    @Override
    void close();
}
