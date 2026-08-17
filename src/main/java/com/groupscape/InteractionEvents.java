package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC dialogue/object interactions aren't per-tick snapshot data like the rest of
 * {@link DataManager}'s {@code DataState} fields - they're discrete events detected between
 * uploads, so this accumulates them into a list (mirroring {@link KillLootDeathEvents}'s
 * accumulator pattern under its own "interactions" key, kept separate from
 * {@code KillLootDeathEvents}'s "events" key so the two event streams don't collide when both
 * write into the same upload payload) and drains everything buffered since the last upload into
 * the "interactions" key on the next {@code consumeState}.
 *
 * Only {@link #onDialogue} exists today (NPC dialogue tracker); object interactions are a
 * separate ticket expected to add an {@code onObjectInteraction} sharing this same accumulator
 * and "interactions" key. Must only be touched from the client thread.
 */
public class InteractionEvents {
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumed;
    private String consumedOwner;

    public synchronized void onDialogue(String playerName, int npcId, String npcName, int combatLevel, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "dialogue");
        event.put("npcId", npcId);
        event.put("npcName", npcName);
        event.put("combatLevel", combatLevel);
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
            output.put("interactions", new ArrayList<>(pending));
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
