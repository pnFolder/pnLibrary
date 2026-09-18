package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigKey
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderUpdater

/**
 * Stores a scalar value in the current action context.
 *
 * The value remains available to later actions and nested conditions in the same
 * execution graph. It is intentionally not persisted between executions.
 *
 * @property key context key to create or replace
 * @property value scalar value exposed to conditions and placeholders
 */
data class SetValueAction(
    val key: String = "",
    val value: String = "",
) : Action {
    override fun execute(context: ActionContext) {
        context.setValue(key, value)
    }
}

/**
 * Copies one context value to another context key.
 *
 * Missing source values are copied as `null`, which makes the destination fail
 * `present` checks until another action supplies it.
 *
 * @property from existing context key
 * @property to destination context key
 */
data class CopyValueAction(
    @field:ConfigKey("from")
    val from: String = "",
    @field:ConfigKey("to")
    val to: String = "",
) : Action {
    override fun execute(context: ActionContext) {
        context.setValue(to, context.value(from))
    }
}

/**
 * Removes a value from the current action context.
 *
 * @property key local context key to remove
 */
data class RemoveValueAction(
    val key: String = "",
) : Action {
    override fun execute(context: ActionContext) {
        context.removeValue(key)
    }
}

/**
 * Updates a writable placeholder registered by this or another library plugin.
 *
 * The reference uses the normal placeholder address without braces, for example
 * `balance` or `pneconomy:balance`. The owning plugin controls conversion,
 * persistence, and write access through its [PlaceholderUpdater].
 *
 * @property reference placeholder key, optionally prefixed with an owner namespace
 * @property value textual assignment passed to the owner-defined updater
 * @property result optional local context key receiving the typed updated value
 */
data class UpdatePlaceholderAction(
    val reference: String = "",
    val value: String = "",
    val result: String? = null,
) : Action {
    override fun execute(context: ActionContext) {
        val updated = context.updatePlaceholder(reference, value)
        result?.takeIf(String::isNotBlank)?.let { context.setValue(it, updated) }
    }
}
