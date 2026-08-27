package com.groupscape.sidepanel;

import com.groupscape.roster.GroupSnapshotMember;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;

/**
 * Builds a {@link GroupSnapshotMember} for the local player straight from Client's live inventory/
 * equipment containers and skill XP, bypassing the group-snapshot HTTP poll entirely - so
 * Bag/Gear/Stats for "you" keep working even when the GroupScape server is unreachable.
 */
public final class LocalGroupSnapshotFactory {
    private static final int INVENTORY_SLOTS = 28;

    private LocalGroupSnapshotFactory() {
    }

    public static GroupSnapshotMember build(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null || player.getName() == null) {
            return null;
        }

        GroupSnapshotMember snapshot = new GroupSnapshotMember(player.getName());
        snapshot.inventory = flatten(client.getItemContainer(InventoryID.INV), INVENTORY_SLOTS);
        snapshot.equipment = flattenEquipment(client.getItemContainer(InventoryID.WORN));
        snapshot.skillXp = skillXp(client);
        return snapshot;
    }

    private static List<Integer> flatten(ItemContainer container, int slotCount) {
        List<Integer> flat = new ArrayList<>(slotCount * 2);
        Item[] items = container != null ? container.getItems() : new Item[0];
        for (int i = 0; i < slotCount; i++) {
            Item item = i < items.length ? items[i] : null;
            flat.add(item != null ? item.getId() : 0);
            flat.add(item != null ? item.getQuantity() : 0);
        }
        return flat;
    }

    private static List<Integer> flattenEquipment(ItemContainer container) {
        int slotCount = EquipmentInventorySlot.values().length;
        List<Integer> flat = new ArrayList<>(Collections.nCopies(slotCount * 2, 0));
        if (container == null) {
            return flat;
        }

        Item[] items = container.getItems();
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
            int idx = slot.getSlotIdx();
            if (idx >= items.length) {
                continue;
            }
            Item item = items[idx];
            if (item == null) {
                continue;
            }
            flat.set(idx * 2, item.getId());
            flat.set(idx * 2 + 1, item.getQuantity());
        }
        return flat;
    }

    private static List<Integer> skillXp(Client client) {
        List<Integer> xp = new ArrayList<>(SkillOrder.SKILLS.length);
        for (Skill skill : SkillOrder.SKILLS) {
            xp.add(client.getSkillExperience(skill));
        }
        return xp;
    }
}
