package org.pnfolder.pnlibrary.core.placeholders;

import java.time.Duration;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Group;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Hide;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.PlayerRef;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("clan")
public class TestClanValues {
    @Placeholder("Clan level")
    public Reply<Integer> level(@Viewer ViewerInfo viewer) {
        viewer.requirePlayerId();
        return Reply.value(12);
    }

    @Placeholder
    public Reply<TestWar> war() {
        return Reply.next(new TestWar());
    }

    @Placeholder
    public final Reply<String> tag = Reply.of("WLF");

    @Placeholder
    public Reply<String> getOwls() {
        return Reply.of("owls");
    }

    @Hide
    @Placeholder
    public Reply<Integer> secret() {
        return Reply.of(1);
    }

    @Placeholder
    public Reply<Integer> top(@Argument(range = "1..100") ArgumentValue<Integer> place) {
        return Reply.of(place.required() * 10);
    }

    @Placeholder
    public Reply<Integer> kills(@Argument ArgumentValue<PlayerRef> who, @Argument(choices = {"day", "week"}) ArgumentValue<String> period) {
        return Reply.of(who.required().getText().length() * 10 + (period.required().equals("week") ? 7 : 0));
    }

    @Placeholder(onInvalid = Invalid.DEFAULT, defaultText = "none")
    public Reply<String> friend(@Argument ArgumentValue<PlayerRef> who) {
        PlayerRef friend = who.getValue();
        return Reply.of(friend == null ? null : friend.getText());
    }

    @Placeholder
    public Reply<String> mine(@Argument ArgumentValue<PlayerRef> who, @Viewer ViewerInfo viewer) {
        PlayerRef player = who.orMe(viewer);
        return Reply.of(player == null ? null : player.getText());
    }

    @Placeholder
    public Reply<Duration> age(@Argument ArgumentValue<Duration> span) {
        return Reply.of(span.required());
    }

    @Placeholder
    public Reply<String> period(@Argument ArgumentValue<TestPeriod> by) {
        return Reply.of(by.required().name());
    }

    @Placeholder
    public Reply<String> query(@Group TestKillsQuery query) {
        return Reply.of(query.who.required().getText() + "-" + query.period.required());
    }

    @Placeholder
    @UseFormat("upper")
    public Reply<String> title() {
        return Reply.of("wolf");
    }

    @Placeholder
    public Reply<Long> money() {
        return Reply.of(1_250_000L);
    }
}
