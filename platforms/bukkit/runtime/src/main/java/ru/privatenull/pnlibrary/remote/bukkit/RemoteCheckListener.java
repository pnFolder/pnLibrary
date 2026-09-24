package ru.privatenull.pnlibrary.remote.bukkit;

/** Optional lifecycle hooks for a remote policy. */
public interface RemoteCheckListener {
    default void allowed(RemoteCheckContext context) { }
    default void denied(RemoteCheckContext context, String reason) { }
    default void failed(Throwable error) { }
}
