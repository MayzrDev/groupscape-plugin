package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Object interactions are discrete events like {@link KillLootDeathEvents}, not per-tick
 * snapshot data - this accumulates them into a list and drains everything buffered since the
 * last upload into the "object_interactions" key on the next {@code consumeState}, mirroring
 * {@link KillLootDeathEvents}'s accumulator pattern. Must only be touched from the client thread.
 */
public class ObjectInteractionEvents {
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumed;
    private String consumedOwner;

    public synchronized void onObjectInteraction(String playerName, int objectId, String objectName, String action, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("objectId", objectId);
        if (objectName != null) {
            event.put("objectName", objectName);
        }
        if (action != null) {
            event.put("action", action);
        }
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
            output.put("object_interactions", new ArrayList<>(pending));
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
