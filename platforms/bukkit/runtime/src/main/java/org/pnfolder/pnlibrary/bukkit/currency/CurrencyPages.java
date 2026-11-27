package org.pnfolder.pnlibrary.bukkit.currency;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.common.text.RussianText;

/** The framed pages of {@code /pn money}: the list of currencies and the history of one. */
final class CurrencyPages {
    private CurrencyPages() {
    }

    static void list(BukkitCommandSender sender, List<Currency> all) {
        ChatKit kit = CurrencyChat.kit(sender);
        String word = RussianText.plural(all.size(), "валюта", "валюты", "валют");
        List<Component> page = new ArrayList<>();
        page.add(kit.header("Валюты", kit.chip(all.size(), word, ChatPalette.ACCENT)));
        page.add(kit.blank());
        for (Currency currency : all) {
            page.add(kit.text("  " + currency.getDescriptor().getDisplayName(), ChatPalette.TEXT).append(kit.dot())
                .append(kit.text(currency.getKey().toString(), ChatPalette.SOFT)));
        }
        send(sender, kit.framed(page));
    }

    /** The last transactions: time, kind, amount, who requested it and why. */
    static void history(BukkitCommandSender sender, Currency currency, CurrencyHistoryPage page) {
        if (page.getItems().isEmpty()) {
            CurrencyChat.configured(sender, currency, ChatTone.INFO, CurrencyChat.settings(currency).getHistoryEmpty());
            return;
        }
        ChatKit kit = CurrencyChat.kit(sender);
        List<Component> rows = new ArrayList<>();
        rows.add(kit.header("История", kit.text(currency.getDescriptor().getDisplayName(), ChatPalette.SOFT)));
        rows.add(kit.blank());
        for (CurrencyTransaction transaction : page.getItems()) {
            rows.add(kit.text("  " + CurrencyChat.TIME.format(transaction.getCreatedAt()) + " ", ChatPalette.SOFT)
                .append(kit.text(CurrencyChat.typeLabel(transaction.getType()), ChatPalette.ACCENT))
                .append(kit.text(" " + currency.format(transaction.getAmount()), ChatPalette.TEXT))
                .append(kit.dot())
                .append(kit.text(transaction.getActor().getType() + ":" + transaction.getActor().getId(), ChatPalette.LABEL))
                .append(kit.dot())
                .append(kit.text(transaction.getReason() != null ? transaction.getReason() : transaction.getService(),
                    ChatPalette.LABEL)));
        }
        send(sender, kit.framed(rows));
    }

    private static void send(BukkitCommandSender sender, List<Component> lines) {
        for (Component line : lines) {
            sender.sendMessage(line);
        }
    }
}
