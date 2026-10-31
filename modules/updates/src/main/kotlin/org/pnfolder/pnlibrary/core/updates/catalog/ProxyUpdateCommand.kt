package org.pnfolder.pnlibrary.core.updates.catalog

import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.time.Duration
import java.util.UUID

/**
 * The `/pn` update controls of the proxies, the same as on Bukkit: the list of plugins, status, check, the catalog
 * (`updates …`), staging one plugin (`update <plugin>`), rollback and its confirmation.
 *
 * It works for the console and for players alike; each action asks for its own permission, and the output is
 * clickable for a sender that can click and plain text for the console. A proxy has no restart command of its own,
 * so a staged update is applied by restarting the proxy the usual way.
 */
class ProxyUpdateCommand(private val library: PnLibrary) {
    private val tokens = UpdateConfirmationTokens()

    /** The catalog commands (`list`, `available`, `info`, `settings`, `set`, `history`, `backups`). */
    val catalog = UpdateCatalogCommands(
        library = library,
        routes = ROUTES,
        viewPermission = VIEW,
        backupsPermission = ROLLBACK,
        managePermission = SETTINGS,
    )

    /** Adds the update commands to the `/pn` root being built. */
    fun install(root: CommandBuilder) {
        root.literal("status") {
            permission(ADMIN, VIEW)
            executes { status(it, null) }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { status(it, it.get<String>("plugin")) }
            }
        }
        root.literal("check") {
            permission(ADMIN, VIEW)
            executes(::check)
        }
        root.literal("updates") {
            permission(ADMIN, VIEW)
            executes(catalog::showList)
            catalog.install { name, configure -> literal(name, configure) }
            literal("rollback") {
                permission(ROLLBACK)
                argument("transaction", ArgumentType.string()) {
                    executes { rollbackSaved(it, false) }
                    literal("configs") { executes { rollbackSaved(it, true) } }
                }
            }
        }
        root.literal("update") {
            permission(ADMIN, DOWNLOAD)
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { update(it, it.get<String>("plugin")) }
            }
            literal("confirm") {
                argument("token", ArgumentType.string()) { executes { confirm(it, it.get<String>("token")) } }
            }
            literal("rollback") { executes(::rollbackPlan) }
        }
        root.literal("support") {
            permission(ADMIN, VIEW)
            executes { say(it.sender, ChatTone.INFO, "Поддержка pnFolder: ${PnLibraryIdentity.SUPPORT_URL}") }
        }
    }

    /** `/pn` alone: the list of plugins for an administrator, otherwise the commands the sender may use. */
    fun overview(context: CommandContext, others: List<Pair<String, String>>) {
        val sender = context.sender
        if (sender.hasPermission(ADMIN)) return catalog.showList(context)
        val available = others.filter { (_, permission) -> sender.hasPermission(permission) }.map { it.first }
        say(sender, ChatTone.INFO, "Команды pnLibrary, доступные вам:", available.joinToString("  ").ifEmpty { "нет" })
    }

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    // --- status and check --------------------------------------------------------------------------------------

    private fun status(context: CommandContext, requested: String?) {
        val sender = context.sender
        val entries = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (requested != null && entries.isEmpty()) return notRegistered(sender, requested)
        val facts = listOf(
            "Java" to (System.getProperty("java.version") ?: "?"),
            PnLibraryIdentity.NAME to library.version.toString(),
        )
        UpdateCatalogView.status(facts, entries.map { it.snapshot }, routes(sender), PnLibraryIdentity.SUPPORT_URL).forEach(sender::send)
    }

    private fun check(context: CommandContext) {
        say(context.sender, ChatTone.PROGRESS, "Проверяю обновления…")
        library.updates.checkNow(actor(context.sender)).whenComplete { _, error ->
            if (error != null) fail(context.sender, "Не удалось проверить обновления", error) else catalog.showAvailable(context)
        }
    }

    // --- update ------------------------------------------------------------------------------------------------

    private fun update(context: CommandContext, name: String) {
        val sender = context.sender
        if (!sender.hasPermission(DOWNLOAD)) return say(sender, ChatTone.FAILURE, "Недостаточно прав для загрузки обновлений.")
        val registration = library.updates.get(name) ?: return notRegistered(sender, name)
        say(sender, ChatTone.PROGRESS, "Ищу совместимое обновление ${registration.snapshot.product}…")
        library.updates.checkNow(actor(sender)).whenComplete { result, error ->
            if (error != null) fail(sender, "Проверка обновления не удалась", error) else stage(sender, registration, result)
        }
    }

    private fun stage(sender: CommandSender, registration: UpdateRegistration, result: UpdatePlanSnapshot) {
        val change = result.plan?.changes?.firstOrNull { it.product.value.equals(registration.snapshot.product, ignoreCase = true) }
        if (result.state != UpdateState.UPDATE_AVAILABLE || change == null) {
            return notices(sender, UpdateStatusMessages.status(result to registration.snapshot))
        }
        val transition = "${change.product} v${change.from} » v${change.to}"
        say(sender, ChatTone.PROGRESS, "Найдено $transition. Скачиваю и проверяю…")
        library.updates.stage(result.id, actor(sender)).whenComplete { _, error ->
            if (error != null) fail(sender, "Не удалось подготовить обновление", error)
            else say(sender, ChatTone.SUCCESS, "Обновление $transition подготовлено.", "Перезапустите прокси, чтобы применить его.")
        }
    }

    // --- rollback and the confirmation ---------------------------------------------------------------------------

    private fun rollbackPlan(context: CommandContext) {
        val sender = context.sender
        if (!sender.hasPermission(ROLLBACK)) return say(sender, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        val plan = library.updates.currentPlan().orElse(null)
            ?: return say(sender, ChatTone.WARNING, "Нет плана обновления, который можно откатить.")
        ask(
            sender, plan.id, plan.revision, UpdateAction.PLAN_ROLLBACK, "Откат обновления",
            listOf("Откатится весь связанный план обновления, а не один плагин.", "Предыдущие JAR применятся после перезапуска."),
        )
    }

    private fun rollbackSaved(context: CommandContext, restoreConfigurations: Boolean) {
        val sender = context.sender
        val id = runCatchingNonFatal { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
            ?: return say(sender, ChatTone.FAILURE, "Некорректный ID резервного набора.")
        val action = if (restoreConfigurations) UpdateAction.ROLLBACK_CONFIGS else UpdateAction.ROLLBACK
        ask(
            sender, id, 0, action, "Откат к резервному набору",
            listOf(
                if (restoreConfigurations) "Вернутся сохранённые JAR и выбранные конфигурации." else "Вернутся сохранённые JAR, конфигурации не изменятся.",
                "Применится после полного перезапуска прокси.",
            ),
        )
    }

    private fun ask(sender: CommandSender, plan: UUID, revision: Long, action: UpdateAction, title: String, lines: List<String>) {
        val token = tokens.issue(identityOf(sender), plan, revision, action, CONFIRMATION_TIME)
        val command = "/pn update confirm $token"
        UpdateCatalogView.confirmation(
            title, lines + "Подтверждение действует 30 секунд.", "[Подтвердить откат]", command, "Выполнить: $command", routes(sender),
        ).forEach(sender::send)
    }

    private fun confirm(context: CommandContext, token: String) {
        val sender = context.sender
        val grant = tokens.redeem(token, identityOf(sender))
            ?: return say(sender, ChatTone.FAILURE, "Подтверждение недействительно, истекло или уже использовано.")
        if (!sender.hasPermission(ROLLBACK)) return say(sender, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        val done = { error: Throwable? ->
            if (error != null) fail(sender, "Не удалось подготовить откат", error)
            else say(sender, ChatTone.SUCCESS, "Предыдущие версии подготовлены.", "Полностью перезапустите прокси.")
        }
        when (grant.action) {
            UpdateAction.PLAN_ROLLBACK -> {
                val current = library.updates.currentPlan().orElse(null)
                if (current == null || current.id != grant.plan || current.revision != grant.revision) {
                    return say(sender, ChatTone.FAILURE, "План обновления изменился; запросите откат заново.")
                }
                library.updates.rollback(actor(sender)).whenComplete { _, error -> done(error) }
            }
            UpdateAction.ROLLBACK -> library.updates.rollback(grant.plan, false, actor(sender)).whenComplete { _, error -> done(error) }
            UpdateAction.ROLLBACK_CONFIGS -> library.updates.rollback(grant.plan, true, actor(sender)).whenComplete { _, error -> done(error) }
            UpdateAction.RESTART, UpdateAction.RESTART_LATER, UpdateAction.RESTART_EMPTY ->
                say(sender, ChatTone.WARNING, "Перезапустите прокси его обычной командой.")
        }
    }

    // --- replies -------------------------------------------------------------------------------------------------

    private fun routes(sender: CommandSender): UpdateCatalogRoutes = ROUTES.copy(interactive = sender.supportsInteraction, ascii = sender.isConsole)

    private fun say(sender: CommandSender, tone: ChatTone, text: String, detail: String? = null) =
        notices(sender, listOf(ChatNotice(tone, text, detail)))

    private fun notices(sender: CommandSender, notices: List<ChatNotice>) =
        UpdateCatalogView.notices(notices, routes(sender)).forEach(sender::send)

    private fun notRegistered(sender: CommandSender, product: String) =
        say(sender, ChatTone.FAILURE, "Плагин $product не зарегистрирован в pnLibrary.")

    private fun fail(sender: CommandSender, title: String, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.last()
        say(sender, ChatTone.FAILURE, "$title.", cause.message ?: cause.javaClass.simpleName)
    }

    private fun identityOf(sender: CommandSender): UUID = UUID.nameUUIDFromBytes(sender.id.toByteArray())

    private fun actor(sender: CommandSender): UpdateActor =
        UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)

    private companion object {
        val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")
        val CONFIRMATION_TIME: Duration = Duration.ofSeconds(30)
        const val ADMIN = "pnlibrary.admin"
        const val VIEW = "pnlibrary.updates.view"
        const val DOWNLOAD = "pnlibrary.updates.download"
        const val ROLLBACK = "pnlibrary.updates.rollback"
        const val SETTINGS = "pnlibrary.updates.settings"
    }
}
