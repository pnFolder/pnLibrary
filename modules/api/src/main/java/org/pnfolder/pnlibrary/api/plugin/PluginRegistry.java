package org.pnfolder.pnlibrary.api.plugin;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryState;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryUsage;

/**
 * Registry of native platform plugins using one pnLibrary runtime.
 *
 * <p>Registration, lookup, removal, and shutdown are serialized and safe from arbitrary threads. {@link #all()}
 * returns a detached immutable snapshot. Closing the registry closes plugins and their modules in reverse ownership
 * order.
 */
@ApiStatus.NonExtendable
public interface PluginRegistry extends AutoCloseable {
    /** Registers {@code owner} or returns its existing registration. */
    @NotNull
    PluginRegistration register(@NotNull Object owner);

    /** Returns {@code owner}'s registration, or {@code null} when absent. */
    @Nullable
    PluginRegistration get(@NotNull Object owner);

    /** Returns {@code owner}'s registration or fails when absent. */
    @NotNull
    default PluginRegistration require(@NotNull Object owner) {
        PluginRegistration registration = get(owner);
        if (registration == null) {
            throw new IllegalStateException(
                "Platform plugin " + owner.getClass().getName() + " is not registered in pnLibrary");
        }
        return registration;
    }

    /** Returns the registration of the plugin with identifier {@code id}, or {@code null} when none is registered. */
    @Nullable
    default PluginRegistration getById(@NotNull PluginId id) {
        for (PluginRegistration registration : all()) {
            if (registration.getId().equals(id)) {
                return registration;
            }
        }
        return null;
    }

    /** Returns the registration of the plugin with identifier {@code id}, or fails naming the registered plugins. */
    @NotNull
    default PluginRegistration requireById(@NotNull PluginId id) {
        PluginRegistration registration = getById(id);
        if (registration != null) {
            return registration;
        }
        StringBuilder names = new StringBuilder();
        for (PluginRegistration other : all()) {
            names.append(names.length() == 0 ? "" : ", ").append(other.getId().getValue());
        }
        throw new IllegalStateException("Plugin " + id + " is not registered in pnLibrary; registered: " + names);
    }

    /** Closes and removes {@code owner}'s registration when present. */
    void unregister(@NotNull Object owner);

    /** Returns an immutable snapshot of registered platform plugins. */
    @NotNull
    List<PluginRegistration> all();

    /** Every module of every plugin registered in the library. */
    @NotNull
    default List<ModuleContext> allModules() {
        List<ModuleContext> modules = new ArrayList<>();
        for (PluginRegistration plugin : all()) {
            modules.addAll(plugin.all());
        }
        return modules;
    }

    /** Every library registered anywhere, and who registered it, so one used by many modules shows once per owner. */
    @NotNull
    default List<LibraryUsage> libraryUsage() {
        List<LibraryUsage> usage = new ArrayList<>();
        for (PluginRegistration plugin : all()) {
            for (LibraryStatus status : plugin.getLibraries().getStatuses()) {
                if (status.getState() != LibraryState.SKIPPED) {
                    usage.add(new LibraryUsage(status.getLibrary(), plugin.getId(), null, status));
                }
            }
            for (ModuleContext module : plugin.all()) {
                for (LibraryStatus status : module.getLibraries().getStatuses()) {
                    if (status.getState() != LibraryState.SKIPPED) {
                        usage.add(new LibraryUsage(status.getLibrary(), plugin.getId(), module.getId(), status));
                    }
                }
            }
        }
        return usage;
    }

    /** Closes all registrations in reverse ownership order. */
    @Override
    void close();
}
