package org.pnfolder.pnlibrary.config;

import java.nio.file.Path;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** The platform of {@link FakeConfigOwner}: it gives each owner its own folder and logger. */
public final class FakeOwnerPlatform implements PlatformOwnership {
    public static final FakeOwnerPlatform INSTANCE = new FakeOwnerPlatform();

    private FakeOwnerPlatform() {
    }

    @Override
    @Nullable
    public Path ownerDataFolder(@NotNull Object owner) {
        return owner instanceof FakeConfigOwner ? ((FakeConfigOwner) owner).getFolder().toPath() : null;
    }

    @Override
    @Nullable
    public Logger ownerLogger(@NotNull Object owner) {
        return owner instanceof FakeConfigOwner ? ((FakeConfigOwner) owner).getLogger() : null;
    }
}
