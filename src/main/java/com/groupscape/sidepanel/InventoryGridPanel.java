package com.groupscape.sidepanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/** 4x7 grid of inventory slots, item icons pulled from {@link ItemManager} the way item-box does on the website. */
class InventoryGridPanel extends JPanel {
    private static final int COLUMNS = 4;
    private static final int SLOT_COUNT = 28;
    private static final Dimension SLOT_SIZE = new Dimension(36, 30);

    private final ItemSlotComponent[] slots = new ItemSlotComponent[SLOT_COUNT];
    private List<Integer> lastRendered = null;

    InventoryGridPanel(ItemManager itemManager, ClientThread clientThread) {
        setLayout(new GridLayout(SLOT_COUNT / COLUMNS, COLUMNS, 1, 1));
        setBackground(SidePanelTheme.SLOT_BG);
        setBorder(new LineBorder(SidePanelTheme.BORDER, 1));

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemSlotComponent slot = new ItemSlotComponent(itemManager, clientThread);
            slot.setPreferredSize(SLOT_SIZE);
            slot.setBackground(new Color(255, 255, 255, 8));
            slot.setIconScale(0.72);
            slots[i] = slot;
            add(slot);
        }
    }

    void setInventory(List<Integer> flatIdQuantityPairs) {
        List<Integer> items = flatIdQuantityPairs == null ? List.of() : flatIdQuantityPairs;
        if (items.equals(lastRendered)) return;
        lastRendered = new ArrayList<>(items);

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int idIndex = slot * 2;
            int id = idIndex < items.size() ? items.get(idIndex) : 0;
            int quantity = idIndex + 1 < items.size() ? items.get(idIndex + 1) : 0;
            slots[slot].setItem(id, quantity);
        }
    }
}
