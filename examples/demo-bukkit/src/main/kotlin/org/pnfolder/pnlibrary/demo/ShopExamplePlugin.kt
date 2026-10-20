package org.pnfolder.pnlibrary.demo

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandProblemException
import org.pnfolder.pnlibrary.api.commands.CommandProblemKind
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A complete example: a small shop plugin that shows every level at which a command can answer a problem.
 *
 * It is not listed in plugin.yml, so it never runs; it exists to be read and to be compiled with the library.
 * To try it, copy the class into a plugin that has `depend: [pnLibrary]`. Nothing is declared in plugin.yml for
 * the commands: pnLibrary registers them itself.
 */
class ShopExamplePlugin : JavaPlugin() {
    private val balances = ConcurrentHashMap<UUID, Int>()
    private var shop: CommandRegistration? = null

    // A problem of our own: raised from the command with CommandProblemException, answered by any level.
    private val noMoney = CommandProblemKind.custom("shop:no-money")

    private val prices = mapOf("sword" to 50, "apple" to 2, "bow" to 35)

    override fun onEnable() {
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling the shop")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 1. Register this plugin and one module of it. All later levels hang off these two objects.
        val registration = library.plugins.register(this)
        val module = registration.registerModule("shop")

        // 2. PLUGIN level: how every command of this plugin, in every module, answers a sender without permission.
        //    A module, command or subcommand that sets its own answer wins over this.
        registration.onCommandDenied { problem ->
            problem.sender.send(Component.text("[Магазин] У вас нет доступа к этой команде.", NamedTextColor.RED))
        }

        // 3. MODULE level: answers for all commands of this module. The text form fills {placeholders} from the problem.
        module.onCommandProblem(CommandProblemKind.PLAYER_NOT_FOUND, "Игрока {value} нет на сервере.")
        module.onCommandProblem(noMoney) { problem ->
            problem.sender.send(Component.text("Не хватает монет: нужно ${problem.detail("price")}.", NamedTextColor.GOLD))
        }

        // 4. The command itself. No plugin.yml entry is needed.
        shop = module.registerCommand("shop") {
            aliases("магазин")
            permission("shop.use")

            // COMMAND level: this wins over the module, the plugin and the library for everything below.
            // A wrong value is INVALID_ARGUMENT; {value}, {argument} and {usage} come from the problem.
            onProblem(CommandProblemKind.INVALID_ARGUMENT, "«{value}» не подходит для «{argument}». Попробуйте: {usage}")
            // Any other typo (unknown word, unfinished command) is USAGE; the handler may do anything.
            onUsage { problem ->
                problem.sender.send(Component.text("Магазин: ${problem.detail("usage")}", NamedTextColor.YELLOW))
            }

            executes { context ->
                context.sender.send(Component.text("Команды: /shop buy <товар> [кол-во], /shop give <игрок> <монеты>"))
            }

            literal("buy") {
                playerOnly() // the console gets the library's "only in game" answer on its own

                // choice(...) is checked and suggested by the client on modern Paper, and suggested everywhere else.
                argument("item", ArgumentType.choice("sword", "apple", "bow")) {
                    executes { buy(it, 1) }

                    // Bounds are checked before the command runs; 0 or 100 produces INVALID_ARGUMENT above.
                    argument("amount", ArgumentType.integer(1, 64)) {
                        executes { buy(it, it.get<Int>("amount")) }
                    }
                }
            }

            literal("give") {
                permission("shop.admin")

                // SUBCOMMAND level: the most specific one. Text, sound, anything.
                onDenied { problem ->
                    problem.sender.send(Component.text("Выдавать монеты могут только администраторы.", NamedTextColor.RED))
                    problem.sender.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1f, 1f))
                }

                // A player argument: a name nobody answers to becomes PLAYER_NOT_FOUND, answered at the module level.
                val onlinePlayer = ArgumentType.player { name ->
                    Bukkit.getPlayerExact(name)?.let { library.audiences.player(it.uniqueId) }
                }
                argument("player", onlinePlayer) {
                    argument("coins", ArgumentType.integer(1, 1000)) {
                        executes { context ->
                            val target = context.get<org.pnfolder.pnlibrary.api.actions.LibraryPlayer>("player")
                            val coins = context.get<Int>("coins")
                            balances.merge(target.uniqueId, coins, Int::plus)
                            context.sender.send(Component.text("${target.name} получил $coins монет."))
                        }
                    }
                }
            }

            literal("reload") {
                consoleOnly() // players get the library's "console only" answer
                executes { it.sender.send(Component.text("Настройки магазина перезагружены.")) }
            }
        }
    }

    /** Takes coins and thanks the buyer; with too little money it raises our own problem instead of printing text. */
    private fun buy(context: CommandContext, amount: Int) {
        val buyer = UUID.fromString(context.sender.id)
        val item = context.get<String>("item")
        val price = prices.getValue(item) * amount
        val balance = balances.getOrDefault(buyer, 100)

        if (balance < price) throw CommandProblemException(noMoney, mapOf("price" to price))

        balances[buyer] = balance - price
        context.sender.send(Component.text("Куплено: $item × $amount за $price. Осталось: ${balance - price}."))
    }

    override fun onDisable() {
        shop?.close()
    }
}
