package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Handle for one plugin-owned polymorphic configuration type. */
public interface ConfigTypeRegistration extends AutoCloseable {
    /** Plugin that published this type. */
    @NotNull
    PluginId getOwner();

    /** Polymorphic interface or abstract class represented by the discriminator. */
    @NotNull
    Class<?> getBaseType();

    /** Concrete implementation created for this registration. */
    @NotNull
    Class<?> getImplementation();

    /** Normalized primary discriminator value. */
    @NotNull
    String getName();

    /** Where the word works: a plugin, a module or one file. */
    @NotNull
    ConfigAddress getTarget();

    /** Whether this registration is still visible to configuration codecs. */
    boolean isActive();

    /** Deactivates and removes this registration. Closing is idempotent. */
    @Override
    void close();
}
