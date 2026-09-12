package com.groupscape;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slayer task assignment/close events for the web site's slayer History/Stats tabs - a discrete
 * event stream like {@link NotableDropEvents}, not per-tick snapshot data (that's still
 * {@link SlayerTaskState}/{@code slayer_task}), so it mirrors that accumulator's
 * pending/consumed/restore pattern rather than {@link ConsumableState}'s latest-value-wins one.
 *
 * <p>Each task assignment gets one stable {@code clientEventId} (a fresh {@link UUID}, minted by
 * {@link #onTaskAssigned}), reused by the later {@link #onTaskClosed} call for that same task so
 * the server can upsert one history row per task instead of writing two. Must only be touched
 * from the client thread.
 */
public class SlayerTaskCloseEvents {
    private final List<Map<String, Object>> pending = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumed;
    private String consumedOwner;

    public synchronized String onTaskAssigned(String playerName, String clientEventId, String taskName,
                                               String masterName, int amountTotal, String modifierType,
                                               Integer modifierValue, Boolean modifierNegative) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("clientEventId", clientEventId);
        event.put("taskName", taskName);
        event.put("masterName", masterName);
        event.put("status", amountTotal > 0 ? "in_progress" : "not_started");
        event.put("amountDone", 0);
        event.put("amountTotal", amountTotal);
        event.put("assignedAt", Instant.now().toString());
        // Fixed for the task's whole lifecycle - not re-sent by onTaskClosed, so the history row
        // keeps whatever this assignment set even though the close event's upsert doesn't touch
        // these columns (see db::upsert_slayer_task_history_event).
        if (modifierType != null) {
            event.put("modifierType", modifierType);
            event.put("modifierValue", modifierValue);
            event.put("modifierNegative", modifierNegative);
        }
        pending.add(event);
        return clientEventId;
    }

    public synchronized void onTaskClosed(String playerName, String clientEventId, String taskName,
                                           String masterName, String status, int amountDone,
                                           int amountTotal, Integer points, String assignedAt) {
        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("clientEventId", clientEventId);
        event.put("taskName", taskName);
        event.put("masterName", masterName);
        event.put("status", status);
        event.put("amountDone", amountDone);
        event.put("amountTotal", amountTotal);
        if (points != null) {
            event.put("points", points);
        }
        event.put("assignedAt", assignedAt);
        event.put("closedAt", Instant.now().toString());
        pending.add(event);
    }

    public static String newClientEventId() {
        return UUID.randomUUID().toString();
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pending.isEmpty()) return;

        String whoIsUpdating = (String) output.get("name");
        if (owner != null && owner.equals(whoIsUpdating)) {
            output.put("slayer_task_events", new ArrayList<>(pending));
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
