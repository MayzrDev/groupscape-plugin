package com.groupscape.sidepanel;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Fixed paperdoll layout of the 11 equipment slots, positions ported from the website's
 * {@code .equipment-*} CSS (site/src/player-equipment/player-equipment.css) and rescaled to the
 * sidepanel's actual ~170px content width - {@code CardLayout} reports its preferred size as the
 * max across every card it holds, not just the visible one, so a wider-than-necessary doll here
 * was forcing every tab (not just Gear) wider than the sidepanel.
 */
class EquipmentDollPanel extends JPanel {
    private static final int SLOT_SIZE = 28;

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final Map<EquipmentInventorySlot, ItemSlotComponent> slots = new EnumMap<>(EquipmentInventorySlot.class);
    private List<Integer> lastRendered = null;

    EquipmentDollPanel(ItemManager itemManager, ClientThread clientThread) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        setLayout(null);
        setOpaque(false);

        addSlot(EquipmentInventorySlot.HEAD, 70, 0);
        addSlot(EquipmentInventorySlot.CAPE, 2, 36);
        addSlot(EquipmentInventorySlot.AMULET, 70, 36);
        addSlot(EquipmentInventorySlot.AMMO, 138, 36);
        addSlot(EquipmentInventorySlot.WEAPON, 2, 72);
        addSlot(EquipmentInventorySlot.BODY, 70, 72);
        addSlot(EquipmentInventorySlot.SHIELD, 138, 72);
        addSlot(EquipmentInventorySlot.LEGS, 70, 108);
        addSlot(EquipmentInventorySlot.GLOVES, 2, 144);
        addSlot(EquipmentInventorySlot.BOOTS, 70, 144);
        addSlot(EquipmentInventorySlot.RING, 138, 144);
        setPreferredSize(new Dimension(170, 176));
    }

    private void addSlot(EquipmentInventorySlot slot, int x, int y) {
        ItemSlotComponent component = new ItemSlotComponent(itemManager, clientThread);
        component.setBounds(x, y, SLOT_SIZE, SLOT_SIZE);
        component.setBackground(SidePanelTheme.SLOT_BG);
        component.setBorder(new LineBorder(SidePanelTheme.BORDER, 1));
        slots.put(slot, component);
        add(component);
    }

    void setEquipment(List<Integer> flatIdQuantityPairs) {
        List<Integer> items = flatIdQuantityPairs == null ? List.of() : flatIdQuantityPairs;
        if (items.equals(lastRendered)) return;
        lastRendered = new ArrayList<>(items);

        for (Map.Entry<EquipmentInventorySlot, ItemSlotComponent> entry : slots.entrySet()) {
            int idIndex = entry.getKey().getSlotIdx() * 2;
            int id = idIndex < items.size() ? items.get(idIndex) : 0;
            int quantity = idIndex + 1 < items.size() ? items.get(idIndex + 1) : 0;
            entry.getValue().setItem(id, quantity);
        }
    }
}
