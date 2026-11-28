package org.pnfolder.pnlibrary.kotlin

import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder

/**
 * A command whose builder is `this`, so nothing is written as `it.`:
 *
 * ```
 * val shop = command("shop") {
 *     permission("shop.use")
 *     executes { ctx -> open(ctx) }
 *     sub("buy") {
 *         arg("item", ArgumentType.string()) { executes { ctx -> buy(ctx, ctx.arg("item")) } }
 *     }
 * }
 * ```
 */
public fun command(name: String, block: CommandBuilder.() -> Unit): CommandDefinition =
    CommandDefinition.command(name) { it.block() }

/** A word after the command, configured with the node as `this`. */
public fun CommandBuilder.sub(name: String, block: CommandNodeBuilder.() -> Unit): CommandBuilder =
    literal(name) { it.block() }

/** A word after another word. */
public fun CommandNodeBuilder.sub(name: String, block: CommandNodeBuilder.() -> Unit): CommandNodeBuilder =
    literal(name) { it.block() }

/** A typed argument after the command. */
public fun <T> CommandBuilder.arg(name: String, type: ArgumentType<T>, block: CommandNodeBuilder.() -> Unit): CommandBuilder =
    argument(name, type) { it.block() }

/** A typed argument after a word or another argument. */
public fun <T> CommandNodeBuilder.arg(name: String, type: ArgumentType<T>, block: CommandNodeBuilder.() -> Unit): CommandNodeBuilder =
    argument(name, type) { it.block() }

/** The value of the argument [name], typed by what the call site expects: `val amount: Int = ctx.arg("amount")`. */
public inline fun <reified T> CommandContext.arg(name: String): T = get(name)
