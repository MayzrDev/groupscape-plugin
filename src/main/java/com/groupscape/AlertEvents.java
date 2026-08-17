package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-HP and wilderness-entry alerts are discrete events like {@link ObjectInteractionEvents},
 * not per-tick snapshot data - this accumulates them into a list and drains everything buffered
 * since the last upload into the "alerts" key on the next {@code consumeState}, mirroring
 * {@link ObjectInteractionEvents}'s accumulator pattern. Must only be touched from the client thread.
 */
public class AlertEvents {
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumed;
    private String consumedOwner;

    public synchronized void onLowHp(String playerName, int currentHp, int maxHp, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "low_hp");
        event.put("currentHp", currentHp);
        event.put("maxHp", maxHp);
        event.put("worldX", worldX);
        event.put("worldY", worldY);
        event.put("plane", plane);
        event.put("world", world);
        pending.add(event);
    }

    public synchronized void onWildernessEntry(String playerName, int wildernessLevel, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "wilderness_entry");
        event.put("wildernessLevel", wildernessLevel);
        event.put("worldX", worldX);
        event.put("worldY", worldY);
        event.put("plane", plane);
        event.put("world", world);
        pending.add(event);
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pending.isEmpty()) return;

        String whoIsUpdating = (String) output.get("name");
        if (owner != null && owner.equals(whoIsUpdating)) {
            output.put("alerts", new ArrayList<>(pending));
        }

        consumed = new ArrayList<>(pending);
        consumedOwner = owner;
        pending.clear();
        owner = null;
    }

    public synchronized void restoreState() {
        if (consumed == null) return;

        pending.addAll(0, consumed);
        if (owner == null) {
            owner = consumedOwner;
        }
        consumed = null;
        consumedOwner = null;
    }
}
