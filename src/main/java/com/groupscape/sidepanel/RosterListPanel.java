package com.groupscape.sidepanel;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.RosterMember;
import java.awt.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

/**
 * Vertical list of {@link MemberCardPanel}s, one per roster member, reconciled on every
 * {@link #refresh} call rather than rebuilt from scratch so a member's collapsed/tab state
 * survives a vitals tick.
 */
public class RosterListPanel extends JPanel implements Scrollable {
    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final ItemManager itemManager;
    private final SkillIconManager skillIconManager;
    private final SpriteManager spriteManager;
    private final ClientThread clientThread;
    private final Map<String, MemberCardPanel> cardsByName = new LinkedHashMap<>();
    private final JLabel moreLabel = new JLabel();

    public RosterListPanel(Client client, GroupScapeTrackerConfig config, ItemManager itemManager,
                            SkillIconManager skillIconManager, SpriteManager spriteManager, ClientThread clientThread) {
        this.client = client;
        this.config = config;
        this.itemManager = itemManager;
        this.skillIconManager = skillIconManager;
        this.spriteManager = spriteManager;
        this.clientThread = clientThread;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        moreLabel.setFont(FontManager.getRunescapeSmallFont());
        moreLabel.setForeground(SidePanelTheme.MUTED);
    }

    /**
     * @param localMember   your own vitals, built by {@code LocalRosterMemberFactory} on the
     *                      client thread (see {@code GroupScapeTrackerPlugin}) and handed in
     *                      ready-made - RuneLite's {@code Client} isn't safe to call from here,
     *                      the Swing EDT this refresh runs on.
     * @param localSnapshot your own bag/gear/stats, built the same way by
     *                      {@link LocalGroupSnapshotFactory}.
     */
    public void refresh(List<RosterMember> rosterMembers, GroupSnapshotState groupSnapshotState,
                         RosterMember localMember, GroupSnapshotMember localSnapshot) {
        String localPlayerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;

        // The roster (vitals) and group snapshot (bag/gear/stats) are both fed by the server -
        // round-tripping through it and its websocket even for your own row, which goes blank the
        // moment the server is unreachable. Using the live local snapshot instead - the same thing
        // PartyFrameOverlay already does for the in-game overlay - means your own row always
        // shows, with no server dependency at all.
        List<RosterMember> members = new ArrayList<>();
        if (!config.sidepanelHideSelf() && localMember != null) {
            members.add(localMember);
        }
        for (RosterMember member : rosterMembers) {
            if (localPlayerName != null && member.name.equalsIgnoreCase(localPlayerName)) continue;
            if (config.sidepanelHideOfflineMembers() && isOffline(member)) continue;
            members.add(member);
        }

        sortMembers(members, localPlayerName);

        int extraCount = 0;
        int maxMembers = config.sidepanelMaxMembers();
        if (maxMembers > 0 && members.size() > maxMembers) {
            extraCount = members.size() - maxMembers;
            members = members.subList(0, maxMembers);
        }

        if (members.isEmpty() && cardsByName.isEmpty()) return;

        boolean orderChanged = members.size() != cardsByName.size();
        for (RosterMember member : members) {
            MemberCardPanel card = cardsByName.get(member.name);
            if (card == null) {
                card = new MemberCardPanel(config, itemManager, skillIconManager, spriteManager, clientThread);
                cardsByName.put(member.name, card);
                orderChanged = true;
            }

            // The local player's own roster row can lag behind (round trip through the server
            // and back down the websocket) even while very much online - PartyFrameOverlay
            // never marks self offline for the same reason, reading self's vitals live from
            // Client instead.
            boolean self = localPlayerName != null && member.name.equalsIgnoreCase(localPlayerName);
            boolean offline = !self && isOffline(member);
            GroupSnapshotMember snapshot = self ? localSnapshot : groupSnapshotState.get(member.name);
            card.update(member, snapshot, offline);
        }

        cardsByName.keySet().retainAll(namesOf(members));

        moreLabel.setText("+" + extraCount + " more");
        moreLabel.setVisible(extraCount > 0);

        if (orderChanged) {
            removeAll();
            for (RosterMember member : members) {
                add(cardsByName.get(member.name));
                add(spacer());
            }
            if (extraCount > 0) {
                add(moreLabel);
            }
            revalidate();
            repaint();
        }
    }

    private void sortMembers(List<RosterMember> members, String localPlayerName) {
        Comparator<RosterMember> comparator;
        switch (config.sidepanelSortOrder()) {
            case ALPHABETICAL:
                comparator = Comparator.comparing(m -> m.name.toLowerCase());
                break;
            case LOWEST_HP_FIRST:
                comparator = Comparator.comparingInt(RosterListPanel::hpRatioForSort);
                break;
            case JOIN_ORDER:
            default:
                return; // roster order already reflects join order
        }

        if (config.sidepanelOfflineMembersLast()) {
            comparator = Comparator.comparing((RosterMember m) -> isOfflineForSort(m, localPlayerName) ? 1 : 0)
                    .thenComparing(comparator);
        }
        members.sort(comparator);
    }

    private static int hpRatioForSort(RosterMember member) {
        if (member.hp == null || member.maxHp == null || member.maxHp == 0) {
            return Integer.MAX_VALUE;
        }
        return (member.hp * 100) / member.maxHp;
    }

    private static boolean isOfflineForSort(RosterMember member, String localPlayerName) {
        boolean self = localPlayerName != null && member.name.equalsIgnoreCase(localPlayerName);
        return !self && isOffline(member);
    }

    private static boolean isOffline(RosterMember member) {
        if (member.lastHeartbeatAt == null) return true;
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= RosterMember.OFFLINE_THRESHOLD_MS;
    }

    private static Set<String> namesOf(List<RosterMember> members) {
        Set<String> names = new HashSet<>();
        for (RosterMember member : members) names.add(member.name);
        return names;
    }

    /**
     * Without this, the {@code JScrollPane} lays this panel out at its own preferred width
     * instead of the viewport's - if the cards' intrinsic content width (icons, digit widths,
     * fixed borders summed up) comes out even a couple pixels wider than the actual panel, it
     * gets silently clipped on the right since horizontal scrolling is disabled, leaving the
     * right-side padding thinner than the left instead of mirroring it. This also keeps the
     * width correct if a vertical scrollbar appears later and narrows the viewport.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    private static JPanel spacer() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(1, 6));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        return panel;
    }
}
