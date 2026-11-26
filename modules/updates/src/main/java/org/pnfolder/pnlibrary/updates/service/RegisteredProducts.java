package org.pnfolder.pnlibrary.updates.service;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;

/** The products plugins registered for updates, as plugins see them. */
final class RegisteredProducts implements UpdateProducts {
    private final UpdateRegistrationStore registrations;

    RegisteredProducts(UpdateRegistrationStore registrations) {
        this.registrations = registrations;
    }

    @NotNull
    @Override
    public List<UpdateRegistration> all() {
        return registrations.publicSnapshot();
    }

    @Nullable
    @Override
    public UpdateRegistration get(@NotNull String product) {
        return registrations.find(product);
    }
}
