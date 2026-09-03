package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drops whose total GE value crosses {@link GroupScapeTrackerConfig#notableDropThreshold()} are
 * discrete events like {@link AlertEvents}, not per-tick snapshot data - this accumulates them
 * into a list and drains everything buffered since the last upload into the "notableDrops" key
 * on the next {@code consumeState}, mirroring {@link AlertEvents}' accumulator pattern. Must
 * only be touched from the client thread.
 */
public class NotableDropEvents {
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumed;
    private String consumedOwner;

    public synchronized void onNotableDrop(String playerName, String sourceType, String sourceName,
                                            int itemId, String itemName, long itemValue, long totalValue) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("sourceType", sourceType);
        event.put("sourceName", sourceName);
        event.put("itemId", itemId);
        event.put("itemName", itemName);
        event.put("itemValue", itemValue);
        event.put("totalValue", totalValue);
        pending.add(event);
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pending.isEmpty()) return;

        String whoIsUpdating = (String) output.get("name");
        if (owner != null && owner.equals(whoIsUpdating)) {
            output.put("notableDrops", new ArrayList<>(pending));
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
