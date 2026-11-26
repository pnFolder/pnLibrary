package org.pnfolder.pnlibrary.api.updates;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The products registered for updates on this server. */
public interface UpdateProducts {
    /** An immutable copy of the current registrations. */
    @NotNull
    List<UpdateRegistration> all();

    /** Finds a registration by product or repository name, ignoring case. */
    @Nullable
    UpdateRegistration get(@NotNull String product);

    /** Finds a registration or fails naming the product that is missing. */
    @NotNull
    default UpdateRegistration require(@NotNull String product) {
        UpdateRegistration registration = get(product);
        if (registration == null) {
            throw new IllegalStateException("Update registration '" + product + "' is unavailable");
        }
        return registration;
    }
}
