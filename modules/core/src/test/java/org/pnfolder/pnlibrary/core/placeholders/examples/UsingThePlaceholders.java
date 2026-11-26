package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

// Example 5: asking for values. Registration is in example 3; here the module is already set up.

/** Everything the service can answer, in the order a plugin usually needs it. */
public class UsingThePlaceholders {
    private final PlaceholderService placeholders;

    public UsingThePlaceholders(ModuleContext context) {
        this.placeholders = context.getPlaceholders();
    }

    /** Text with several addresses: each {@code {...}} is replaced, an unknown one stays as written. */
    public CompletionStage<String> message(UUID player) {
        return placeholders.render("Clan {clan.level}, top {stats.top.1}, balance {bank.balance}", player);
    }

    /** Values of the caller win over registered ones: {@code {reward}} is not a placeholder, but it is filled. */
    public CompletionStage<String> withOwnData(UUID player) {
        return placeholders.render("You get {reward} for {clan.name}", ViewerInfo.of(player), Collections.<String, Object>singletonMap("reward", 100));
    }

    /** One value, not text: the type stays (Integer here), so it can be compared or calculated. */
    public CompletionStage<Object> level(UUID player) {
        return placeholders.resolve("clan.level", player);
    }

    /** Known or not, without evaluating anything: for commands that check an address the player typed. */
    public boolean known(String address) {
        return placeholders.contains(address);
    }

    /** What is registered, with description, owner, type, default format and PlaceholderAPI names. */
    public List<String> describe() {
        List<String> lines = new ArrayList<String>();
        for (PlaceholderInfo info : placeholders.list()) {
            lines.add("{" + info.getAddress() + "} " + info.getType() + " " + info.getDescription() + " (" + info.getOwner() + ") "
                    + String.join(", ", info.getPublished()));
        }
        return lines;
    }

    /** The outside systems. {@code %...%} of PlaceholderAPI is filled by {@code render} itself when an adapter is available. */
    public List<String> outside() {
        List<String> lines = new ArrayList<String>();
        for (PlaceholderAdapter adapter : placeholders.adapters().all()) {
            lines.add(adapter.getId() + ": " + (adapter.isAvailable() ? "available" : "unavailable"));
        }
        return lines;
    }

    /** Closing the handle removes the adapter; closing the module removes everything else. */
    public AutoCloseable connectOwnSystem() {
        return placeholders.adapters().register(new OwnSystem());
    }
}
