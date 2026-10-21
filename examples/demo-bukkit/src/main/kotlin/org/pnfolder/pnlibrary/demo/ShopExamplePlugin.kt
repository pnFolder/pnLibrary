package org.pnfolder.pnlibrary.demo

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.BadUsage
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.PlayerNotFound
import org.pnfolder.pnlibrary.api.commands.ensure
import org.pnfolder.pnlibrary.api.plugin.onCommandError
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An error of this plugin. It is an ordinary class, so a handler gets real fields, not a map of strings. */
class NoMoney(val price: Int) : CommandError() {
    override fun placeholders() = mapOf("price" to price) // for texts such as "Нужно {price} монет"
}

/**
 * A complete example: a small shop plugin that shows every level at which a command can answer an error.
 *
 * It is not listed in plugin.yml, so it never runs; it exists to be read and to be compiled with the library.
 * To try it, copy the classes into a plugin that has `depend: [pnLibrary]`. Nothing is declared in plugin.yml for
 * the commands: pnLibrary registers them itself.
 */
class ShopExamplePlugin : JavaPlugin() {
    private val balances = ConcurrentHashMap<UUID, Int>()
    private var shop: CommandRegistration? = null
    private val prices = mapOf("sword" to 50, "apple" to 2, "bow" to 35)

    override fun onEnable() {
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling the shop")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 1. Register this plugin and one module of it. Every later level hangs off these two objects.
        val registration = library.plugins.register(this)
        val module = registration.registerModule("shop")

        // 2. PLUGIN level: how every command of this plugin, in every module, answers a sender without permission.
        //    In the lambda `this` is the command context, so `sender` is right there.
        registration.onCommandError<NoPermission> {
            sender.send(Component.text("[Магазин] У вас нет доступа к этой команде.", NamedTextColor.RED))
        }

        // 3. MODULE level: answers for all commands of this module. A text may use the error's placeholders.
        module.onCommandError<PlayerNotFound>("Игрока {value} нет на сервере.")
        module.onCommandError<NoMoney> { error ->
            sender.send(Component.text("Не хватает монет: нужно ${error.price}.", NamedTextColor.GOLD))
        }

        // 4. The command itself. No plugin.yml entry is needed.
        shop = module.registerCommand("shop") {
            aliases("магазин")

            // PERMISSION CHECK. You only declare the permission; pnLibrary checks it itself, BEFORE any code below
            // runs. A sender without "shop.use" never reaches `executes`: the library raises NoPermission("shop.use")
            // and it is answered by the levels below (here: the plugin level, step 2).
            permission("shop.use")

            // COMMAND level: wins over the module, the plugin and the library for everything below.
            // A handler for a base class also answers its subclasses: BadUsage covers a wrong argument, an
            // unknown word and an unfinished command; the more specific InvalidArgument is answered first.
            onError<InvalidArgument>("«{value}» не подходит для «{argument}». Попробуйте: {usage}")
            onError<BadUsage> { error -> sender.send(Component.text("Магазин: ${error.usage}", NamedTextColor.YELLOW)) }

            executes { it.sender.send(Component.text("Команды: /shop buy <товар> [кол-во], /shop give <игрок> <монеты>")) }

            literal("buy") {
                playerOnly() // the console gets the library's "only in game" answer on its own

                // choice(...) is checked and suggested by the client on modern Paper, and suggested everywhere else.
                argument("item", ArgumentType.choice("sword", "apple", "bow")) {
                    executes { buy(it, 1) }

                    // The bounds are checked before the command runs; 0 or 100 is an InvalidArgument.
                    argument("amount", ArgumentType.integer(1, 64)) {
                        executes { buy(it, it.get<Int>("amount")) }
                    }
                }
            }

            literal("give") {
                // The same check, on a subcommand only: /shop give needs "shop.admin" on top of "shop.use".
                permission("shop.admin")

                // SUBCOMMAND level: the most specific one. Text, sound, anything.
                onError<NoPermission> {
                    sender.send(Component.text("Выдавать монеты могут только администраторы.", NamedTextColor.RED))
                    sender.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1f, 1f))
                }

                // A name nobody answers to becomes PlayerNotFound, which the module answers above.
                val onlinePlayer = ArgumentType.player { name ->
                    Bukkit.getPlayerExact(name)?.let { library.audiences.player(it.uniqueId) }
                }
                argument("player", onlinePlayer) {
                    argument("coins", ArgumentType.integer(1, 1000)) {
                        executes { context ->
                            val target = context.get<LibraryPlayer>("player")
                            val coins = context.get<Int>("coins")
                            balances.merge(target.uniqueId, coins, Int::plus)
                            context.sender.send(Component.text("${target.name} получил $coins монет."))
                        }
                    }
                }
            }

            literal("reload") {
                consoleOnly() // a player gets the library's "console only" answer
                executes { it.sender.send(Component.text("Настройки магазина перезагружены.")) }
            }
        }
    }

    /** Takes the coins; with too little money the command stops with our own error, and the module answers it. */
    private fun buy(context: CommandContext, amount: Int) {
        val buyer = UUID.fromString(context.sender.id)
        val item = context.get<String>("item")

        // A permission check made in code, for when it depends on what was asked for: only VIPs may buy the bow.
        // `ensure(condition) { error }` reads "this must be true, otherwise stop with this error". Here it must be
        // true that the sender has "shop.vip"; the check applies only when the item is the bow.
        // It raises the very same NoPermission error, so the same five levels answer it, and the command stops here.
        if (item == "bow") {
            context.ensure(context.sender.hasPermission("shop.vip")) { NoPermission("shop.vip") }
        }

        val price = prices.getValue(item) * amount
        val balance = balances.getOrDefault(buyer, 100)

        context.ensure(balance >= price) { NoMoney(price) }

        balances[buyer] = balance - price
        context.sender.send(Component.text("Куплено за $price. Осталось: ${balance - price}."))
    }

    override fun onDisable() {
        shop?.close()
    }
}
