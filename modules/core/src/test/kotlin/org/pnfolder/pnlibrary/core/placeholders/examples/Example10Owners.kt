package org.pnfolder.pnlibrary.core.placeholders.examples

import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import org.pnfolder.pnlibrary.api.plugin.ModuleContext

// Example 10: owners, visibility and placeholders inside placeholders.
//
// Everything a module registers belongs to it (`plugin/module`) and is removed when the module closes.
// Who may use a placeholder is its Visibility, set on the property, the method or the class: MODULE (only this module),
// PLUGIN (every module of the plugin), ALL (everyone, default). Formats and converters are always open.
// A name that several owners have is answered by the nearest: own module, own plugin, the others, the library.

/** `{shop.price}` is open to everyone; `{shop.counter}` only to the module that registered it. Put on a class, it limits all of it. */
@Placeholders("shop")
internal class ShopPrices {
    @Placeholder("Price of the item in hand")
    val price: Reply<Int> get() = Reply.of(10)

    @Placeholder("Internal counter", visibility = Visibility.MODULE)
    val counter: Reply<Int> get() = Reply.of(42)
}

internal fun ModuleContext.setupOwners() {
    placeholders.register(ShopPrices(), papi = null)
}

// Writing the owner. It stands in front of the address, ended by `@`, and the same works in front of a format:
//
//   {shop.price}                       the nearest one                      (own module, own plugin, others, library)
//   {shop/main@shop.price}             exactly the module main of the plugin shop
//   {shop@shop.price}                  any module of the plugin shop
//   {~@shop.price}                     only my plugin   (any of its modules)
//   {~~@shop.price}                    only my module
//   {~~~@shop.price}                   only the library
//   {shop.price|~~~@upper}             a format of the library
//   {shop.price|shop/main@pad:4}       a format of that module
//
// Values passed to one run (`values` of render, `[item]` in actions) have no owner: they are read first when no owner is
// written, and skipped when one is.
//
// A placeholder inside a placeholder. The inner one is read first and its value takes its place; a value that holds a
// dot or a space is put in quotes, so it stays one word. Up to 16 levels.
//
//   {clan.of.{player.name}.level}      the level of the clan of the player whose name {player.name} gives
//   {score|pad:{width}}               an argument of a format
//   {{owner}@shop.price}              even the owner
//
// When an inner placeholder gives no value, the whole text stays as written.
//
// `/pn placeholders info <address>` and the placeholder menu show every one of these forms for a placeholder.
