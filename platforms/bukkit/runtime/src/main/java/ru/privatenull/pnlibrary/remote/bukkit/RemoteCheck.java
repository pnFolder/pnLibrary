package ru.privatenull.pnlibrary.remote.bukkit;

/** Implemented by the remote policy JAR. Keep this interface stable between host and policy releases. */
public interface RemoteCheck {
    RemoteCheckResult check(RemoteCheckContext context) throws Exception;
}
