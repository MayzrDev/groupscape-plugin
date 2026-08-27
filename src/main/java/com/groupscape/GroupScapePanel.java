package com.groupscape;

import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterState;
import com.groupscape.sidepanel.RosterListPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidepanel showing the party/group roster like the in-game overlay does (HP/prayer/run/spec,
 * active prayers, target) plus per-member inventory/equipment/stats dropdowns like the website's
 * player panel. A Swing {@link Timer} pulls from {@link RosterState} (live vitals, via websocket)
 * and {@link GroupSnapshotState} (inventory/equipment/skills, via periodic HTTP poll) rather than
 * being pushed to directly, since both are written from background threads.
 */
class GroupScapePanel extends PluginPanel {
    private static final int REFRESH_MS = 600;

    private final RosterListPanel rosterListPanel;
    private final Timer refreshTimer;

    GroupScapePanel(
            Runnable onOpenGroupScape,
            Client client,
            GroupScapeTrackerConfig config,
            RosterState rosterState,
            GroupSnapshotState groupSnapshotState,
            ItemManager itemManager,
            SkillIconManager skillIconManager,
            SpriteManager spriteManager,
            ClientThread clientThread,
            Supplier<RosterMember> localMemberSupplier,
            Supplier<GroupSnapshotMember> localSnapshotSupplier
    ) {
        super(false);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton openButton = new JButton("Open GroupScape");
        openButton.setPreferredSize(new Dimension(0, 30));
        openButton.addActionListener(event -> onOpenGroupScape.run());
        add(openButton, BorderLayout.NORTH);

        rosterListPanel = new RosterListPanel(client, config, itemManager, skillIconManager, spriteManager, clientThread);
        JScrollPane scrollPane = new JScrollPane(rosterListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        // BLIT_SCROLL_MODE (the default) copies existing pixels around on scroll/resize instead
        // of repainting them - a card growing when a tab opens shifts everything below it, and
        // that blit copy was leaving stale fragments (a card's old header/vitals) behind in
        // whatever now-different content scrolled into that spot. Always repainting properly
        // avoids the stale copy entirely.
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        add(scrollPane, BorderLayout.CENTER);

        refreshTimer = new Timer(REFRESH_MS, e -> rosterListPanel.refresh(
                rosterState.all(), groupSnapshotState, localMemberSupplier.get(), localSnapshotSupplier.get()));
        refreshTimer.start();
    }
}
