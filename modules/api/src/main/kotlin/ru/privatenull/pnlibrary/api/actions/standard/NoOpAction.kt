package ru.privatenull.pnlibrary.api.actions

/**
 * Intentionally performs no work.
 *
 * Use this action for an explicit silent branch, such as the `default` case of
 * a [SwitchAction] when unknown values should be ignored without logging.
 */
class NoOpAction : Action {
    override fun execute(context: ActionContext) = Unit
}
