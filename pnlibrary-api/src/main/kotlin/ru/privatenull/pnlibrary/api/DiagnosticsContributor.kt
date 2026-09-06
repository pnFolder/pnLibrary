package ru.privatenull.pnlibrary.api

/**
 * Supplies a bounded snapshot owned by another plugin.
 * Called only while a diagnostic report is being collected.
 *
 * Implementations should be **fast and non-blocking**. Heavy computation is
 * forbidden — the collection runs on a background thread, but a contributor
 * that blocks too long will be cancelled by the collector's timeout budget.
 */
fun interface DiagnosticsContributor {
    /** Stable identifier for this contributor, max 96 chars, pattern `[A-Za-z0-9_.-]+`. */
    val id: String get() = javaClass.simpleName

    /**
     * Returns the current diagnostic snapshot as a `Map<String, Any?>`.
     * Must not throw; wrap errors in a safe result string if needed.
     */
    fun collect(): Map<String, Any?>

    /** Optional list of configuration file paths relative to the plugin data folder. */
    fun configurationFiles(): Collection<String> = emptyList()

    /**
     * Rich per-file rules with redaction policies.
     * Existing implementations may keep returning only [configurationFiles].
     */
    fun configurations(): Collection<DiagnosticConfiguration> = emptyList()
}
