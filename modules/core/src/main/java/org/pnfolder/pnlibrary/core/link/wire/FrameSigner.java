package org.pnfolder.pnlibrary.core.link.wire;

import org.jetbrains.annotations.NotNull;

/** Signs a message body with the shared link key. */
public interface FrameSigner {
    @NotNull byte[] sign(@NotNull byte[] bytes, @NotNull byte[] key);
}
