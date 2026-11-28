package org.pnfolder.pnlibrary.api.network.message;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** A list written as its length followed by the items one after another. */
final class ListCodec<T> implements MessageCodec<List<T>> {
    private final MessageCodec<T> item;

    ListCodec(MessageCodec<T> item) {
        this.item = item;
    }

    @Override
    public void encode(@NotNull List<T> items, @NotNull MessageWriter out) {
        out.writeVarInt(items.size());
        for (T next : items) {
            item.encode(next, out);
        }
    }

    @NotNull
    @Override
    public List<T> decode(@NotNull MessageReader in) {
        int count = in.readVarInt();
        List<T> items = new ArrayList<>(Math.min(count, in.remaining())); // a forged count must not reserve memory
        for (int i = 0; i < count; i++) {
            items.add(item.decode(in));
        }
        return items;
    }
}
