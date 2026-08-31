package com.groupscape.sidepanel;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/** Equipment tab content: the paperdoll on top, combat bonus totals stacked below it. */
class EquipmentTabPanel extends JPanel {
    private final EquipmentDollPanel dollPanel;
    private final EquipmentBonusesPanel bonusesPanel;

    EquipmentTabPanel(ItemManager itemManager, ClientThread clientThread) {
        this.dollPanel = new EquipmentDollPanel(itemManager, clientThread);
        this.bonusesPanel = new EquipmentBonusesPanel(itemManager, clientThread);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // FlowLayout centers a fixed-size child within whatever width it's actually given -
        // BoxLayout's own cross-axis alignmentX centering doesn't reliably kick in here since the
        // doll sits behind a CardLayout that resizes its visible card to fill the container
        // outright rather than respecting the card's reported max size.
        JPanel dollWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        dollWrapper.setOpaque(false);
        dollWrapper.setAlignmentX(LEFT_ALIGNMENT);
        dollWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, dollPanel.getPreferredSize().height));
        dollWrapper.add(dollPanel);
        add(dollWrapper);
        add(bonusesPanel);
    }

    void setEquipment(List<Integer> flatIdQuantityPairs) {
        dollPanel.setEquipment(flatIdQuantityPairs);
        bonusesPanel.setEquipment(flatIdQuantityPairs);
    }
}
