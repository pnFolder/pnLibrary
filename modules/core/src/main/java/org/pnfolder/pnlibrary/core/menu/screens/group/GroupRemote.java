package org.pnfolder.pnlibrary.core.menu.screens.group;

import java.util.List;
import java.util.function.BiConsumer;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.remote.LinkNames;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.GroupEdit;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.remote.RemoteMenuData;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;

/** Reads and changes the server groups, which live on the proxy, away from the server thread. */
final class GroupRemote {
    private final MenuSession session;
    private volatile MenuData proxyData;

    GroupRemote(MenuSession session) {
        this.session = session;
    }

    private MenuData proxy() {
        MenuData current = proxyData;
        if (current == null) {
            current = new RemoteMenuData(LinkNames.PROXY, session.getHost().getLink().getData());
            proxyData = current;
        }
        return current;
    }

    /** Reads the groups from the proxy, then runs {@code then} on the server thread with them and the last network map. */
    void withGroups(MenuViewer player, Runnable noNetwork, BiConsumer<List<ServerGroup>, NetworkReport> then) {
        if (session.getHost().getLink().getNetwork() == null) {
            noNetwork.run();
            return;
        }
        NetworkReport map = session.getHost().getLink().getNetwork().getReport();
        session.getHost().async(() -> {
            try {
                List<ServerGroup> loaded = proxy().groups();
                session.getHost().sync(() -> then.accept(loaded, map));
            } catch (Exception error) {
                session.getHost().sync(() -> session.tell(player, ChatTone.FAILURE, "Прокси не ответил.", Throwables.rootMessage(error)));
            }
        });
    }

    void edit(MenuViewer player, GroupEdit edit, Runnable then) {
        session.allowed(player, MenuConstants.GROUPS_PERMISSION, () -> session.getHost().async(() -> {
            try {
                boolean done = proxy().editGroup(edit);
                session.getHost().sync(() -> {
                    if (done) {
                        then.run();
                    } else {
                        session.tell(player, ChatTone.FAILURE, "Не получилось изменить группу " + edit.getGroup() + ".");
                    }
                });
            } catch (Exception error) {
                session.getHost().sync(() -> session.tell(player, ChatTone.FAILURE, "Прокси не ответил.", Throwables.rootMessage(error)));
            }
        }));
    }
}
