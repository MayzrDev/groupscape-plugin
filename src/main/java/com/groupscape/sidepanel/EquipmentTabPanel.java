package com.groupscape.sidepanel;

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

        add(dollPanel);
        add(bonusesPanel);
    }

    void setEquipment(List<Integer> flatIdQuantityPairs) {
        dollPanel.setEquipment(flatIdQuantityPairs);
        bonusesPanel.setEquipment(flatIdQuantityPairs);
    }
}
