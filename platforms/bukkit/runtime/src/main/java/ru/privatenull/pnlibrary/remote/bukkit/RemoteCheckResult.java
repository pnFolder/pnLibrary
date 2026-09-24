package ru.privatenull.pnlibrary.remote.bukkit;

/** Decision returned by a remote policy class. */
public final class RemoteCheckResult {
    private final boolean allowed;
    private final String message;

    private RemoteCheckResult(boolean allowed, String message) { this.allowed = allowed; this.message = message; }
    public boolean allowed() { return allowed; }
    public String message() { return message; }
    public static RemoteCheckResult allow() { return new RemoteCheckResult(true, ""); }
    public static RemoteCheckResult deny(String message) {
        if (message == null || message.trim().isEmpty()) throw new IllegalArgumentException("deny message is required");
        return new RemoteCheckResult(false, message.trim());
    }
}
