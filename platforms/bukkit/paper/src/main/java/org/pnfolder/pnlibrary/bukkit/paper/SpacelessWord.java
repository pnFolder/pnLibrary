package org.pnfolder.pnlibrary.bukkit.paper;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

// Brigadier's built-in word type stops at `/`, `:`, `;` and similar, breaking ids like `shop/main:config.yml`.
// Declared as greedy to the client so those chars are not painted red; the server reads it as one token.
@SuppressWarnings("UnstableApiUsage")
final class SpacelessWord implements CustomArgumentType<String, String> {
    static final SpacelessWord INSTANCE = new SpacelessWord();

    private SpacelessWord() {
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isWhitespace(char symbol) {
        return Character.isWhitespace(symbol) || Character.isSpaceChar(symbol);
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.greedyString();
    }
}
