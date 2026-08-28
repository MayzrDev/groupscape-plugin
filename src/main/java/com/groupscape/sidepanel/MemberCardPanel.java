package com.groupscape.sidepanel;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.RosterMember;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

/**
 * One member's row in the roster sidepanel: a collapsible card with vitals bars (ported from
 * {@code PartyFrameOverlay}) up top, and a minibar that swaps in inventory/equipment/skills
 * below - the sidepanel counterpart to the in-game overlay, matching the approved mockup.
 */
class MemberCardPanel extends JPanel {
    private static final String TAB_INVENTORY = "inventory";
    private static final String TAB_EQUIPMENT = "equipment";
    private static final String TAB_SKILLS = "skills";

    private final GroupScapeTrackerConfig config;
    private final JPanel stripe = new JPanel();
    private final JLabel nameLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final VitalsBarsPanel vitalsBarsPanel = new VitalsBarsPanel();
    private final ActivePrayerIconsPanel prayerIconsPanel;
    private final JPanel body = new JPanel();
    private final JPanel content = new JPanel(new CardLayout());
    private final InventoryGridPanel inventoryPanel;
    private final EquipmentTabPanel equipmentPanel;
    private final SkillsGridPanel skillsPanel;
    private final JLabel inventoryTabButton;
    private final JLabel equipmentTabButton;
    private final JLabel skillsTabButton;

    private boolean collapsed = false;
    private String activeTab = null;
    private String lastLayoutSignature = null;

    MemberCardPanel(GroupScapeTrackerConfig config, ItemManager itemManager, SkillIconManager skillIconManager,
                     SpriteManager spriteManager, ClientThread clientThread) {
        this.config = config;
        this.prayerIconsPanel = new ActivePrayerIconsPanel(spriteManager);
        this.inventoryPanel = new InventoryGridPanel(itemManager, clientThread);
        this.equipmentPanel = new EquipmentTabPanel(itemManager, clientThread);
        this.skillsPanel = new SkillsGridPanel(skillIconManager);

        setLayout(new BorderLayout());
        setBackground(SidePanelTheme.CARD_BG);
        setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, SidePanelTheme.BORDER), new EmptyBorder(0, 0, 0, 0)));

        stripe.setPreferredSize(new Dimension(3, 0));
        stripe.setBackground(SidePanelTheme.ACCENT);
        add(stripe, BorderLayout.WEST);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        add(outer, BorderLayout.CENTER);

        outer.add(buildHeader(), BorderLayout.NORTH);

        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4, 8, 8, 8));
        outer.add(body, BorderLayout.CENTER);

        body.add(vitalsBarsPanel);
        body.add(spacer(4));
        body.add(prayerIconsPanel);
        prayerIconsPanel.setVisible(false);
        body.add(spacer(4));

        JPanel minibar = new JPanel(new java.awt.GridLayout(1, 3, 4, 0));
        minibar.setOpaque(false);
        minibar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 1, 0, SidePanelTheme.BORDER), new EmptyBorder(4, 0, 4, 0)));
        minibar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        inventoryTabButton = tabButton("Bag", () -> selectTab(TAB_INVENTORY));
        equipmentTabButton = tabButton("Gear", () -> selectTab(TAB_EQUIPMENT));
        skillsTabButton = tabButton("Stats", () -> selectTab(TAB_SKILLS));
        minibar.add(inventoryTabButton);
        minibar.add(equipmentTabButton);
        minibar.add(skillsTabButton);
        body.add(minibar);
        body.add(spacer(4));

        content.setOpaque(false);
        content.add(inventoryPanel, TAB_INVENTORY);
        content.add(equipmentPanel, TAB_EQUIPMENT);
        content.add(skillsPanel, TAB_SKILLS);
        content.setVisible(false);
        body.add(content);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(6, 8, 6, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setCollapsed(!collapsed);
            }
        });

        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(SidePanelTheme.TEXT);
        header.add(nameLabel, BorderLayout.CENTER);

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(SidePanelTheme.MUTED);
        header.add(statusLabel, BorderLayout.EAST);

        return header;
    }

    /**
     * A plain {@link JButton} here left the RuneLite look-and-feel painting its own button chrome
     * on top of our background color, so two tabs could look "active" at once depending on the
     * button's internal armed/rollover state. A JLabel we fully paint ourselves - the same
     * approach the header already uses for its collapse toggle - has no such interference.
     */
    private JLabel tabButton(String text, Runnable onClick) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setBackground(SidePanelTheme.CARD_BG);
        label.setForeground(SidePanelTheme.MUTED);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setPreferredSize(new Dimension(0, 24));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }
        });
        return label;
    }

    /** Clicking the already-active tab collapses the content section back down instead of re-showing it. */
    private void selectTab(String tab) {
        if (tab.equals(activeTab)) {
            activeTab = null;
            content.setVisible(false);
        } else {
            activeTab = tab;
            ((CardLayout) content.getLayout()).show(content, tab);
            content.setVisible(true);
        }

        highlightTab(inventoryTabButton, TAB_INVENTORY.equals(activeTab));
        highlightTab(equipmentTabButton, TAB_EQUIPMENT.equals(activeTab));
        highlightTab(skillsTabButton, TAB_SKILLS.equals(activeTab));
        revalidate();
        repaint();
    }

    private void highlightTab(JLabel label, boolean active) {
        label.setBackground(active ? new Color(255, 152, 31, 36) : SidePanelTheme.CARD_BG);
        label.setForeground(active ? SidePanelTheme.ACCENT : SidePanelTheme.MUTED);
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        body.setVisible(!collapsed);
        revalidate();
        repaint();
    }

    private static JPanel spacer(int height) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(1, height));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return panel;
    }

    void update(RosterMember member, GroupSnapshotMember snapshot, boolean offline) {
        nameLabel.setText(member.name);
        nameLabel.setForeground(offline ? SidePanelTheme.MUTED_DIM : SidePanelTheme.TEXT);
        stripe.setBackground(offline ? SidePanelTheme.OFFLINE : SidePanelTheme.memberColor(member.color));

        if (offline) {
            statusLabel.setText("Offline");
        } else if (member.world != null && !config.sidepanelHideWorld()) {
            statusLabel.setText("W" + member.world);
        } else {
            statusLabel.setText("");
        }

        vitalsBarsPanel.setVisible(!offline);
        vitalsBarsPanel.setVisibleBars(!config.sidepanelHideHp(), !config.sidepanelHidePrayer(),
                !config.sidepanelHideRun(), !config.sidepanelHideSpec(), !config.sidepanelHideTarget());
        vitalsBarsPanel.setMember(member);

        boolean showPrayerIcons = !offline && !config.sidepanelHidePrayer() && !config.sidepanelHidePrayerIcons();
        prayerIconsPanel.setActivePrayers(showPrayerIcons ? member.activePrayers : java.util.List.of());

        if (snapshot != null) {
            inventoryPanel.setInventory(snapshot.inventory);
            equipmentPanel.setEquipment(snapshot.equipment);
            skillsPanel.setSkillXp(snapshot.skillXp);
        }

        inventoryTabButton.setVisible(config.sidepanelShowInventoryTab());
        equipmentTabButton.setVisible(config.sidepanelShowEquipmentTab());
        skillsTabButton.setVisible(config.sidepanelShowSkillsTab());

        // A tab can be toggled off in config while it's the currently expanded one - collapse
        // back down rather than leaving a section open with no way left to reach it.
        if ((TAB_INVENTORY.equals(activeTab) && !config.sidepanelShowInventoryTab())
                || (TAB_EQUIPMENT.equals(activeTab) && !config.sidepanelShowEquipmentTab())
                || (TAB_SKILLS.equals(activeTab) && !config.sidepanelShowSkillsTab())) {
            selectTab(activeTab);
        }

        // Bag/gear/stats content (item icons, skill XP, etc.) repaints itself and never changes
        // this card's size - only these flags do. Revalidating on every 600ms refresh regardless
        // was forcing the whole RosterListPanel's BoxLayout to relayout every tick, which under
        // Windows' hardware-accelerated Swing pipeline could paint a frame with a sibling card's
        // stale pixels bleeding through before the real repaint caught up (the same ghosting the
        // scroll-mode fix above addresses for actual card growth). Only revalidate when one of
        // these actually changed.
        String layoutSignature = offline + "|" + config.sidepanelHideHp() + "|" + config.sidepanelHidePrayer()
                + "|" + config.sidepanelHideRun() + "|" + config.sidepanelHideSpec() + "|" + config.sidepanelHideTarget()
                + "|" + config.sidepanelShowInventoryTab() + "|" + config.sidepanelShowEquipmentTab()
                + "|" + config.sidepanelShowSkillsTab() + "|" + activeTab;
        if (!layoutSignature.equals(lastLayoutSignature)) {
            lastLayoutSignature = layoutSignature;
            revalidate();
        }
        repaint();
    }

    /**
     * Without this, {@code RosterListPanel}'s vertical BoxLayout hands this card all the leftover
     * space in the scroll viewport (its default maximumSize is effectively unbounded), stretching
     * a single member's card to fill the whole sidepanel. Capping the height to the preferred size
     * keeps the card exactly as tall as its content - collapsed, or expanded by one tab's worth.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
