package com.groupscape;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correlates a raid's completion signal (chat message for CoX/ToA, the reward-chest
 * {@code LootReceived} itself for ToB - see {@link GroupScapeTrackerPlugin#onChatMessage}/
 * {@link GroupScapeTrackerPlugin#onLootReceived}) with the reward-chest loot that follows it, and
 * queues one "raid"-typed event per completion for the next upload.
 *
 * Mirrors {@link KillLootDeathEvents}'s pending-kill/unmatched-loot correlation shape: a chat
 * completion is held in {@link #pendingCox}/{@link #pendingToa} until a matching-name chest
 * {@code LootReceived} arrives within {@link #CORRELATION_WINDOW_MILLIS}, at which point the two
 * combine into one queued event. ToB has no chat signal at all, so its chest loot alone is the
 * completion. A chest loot that never gets a matching chat signal within the window (CoX/ToA) is
 * dropped rather than reported without a difficulty - same "best-effort, no completion shipped
 * without both halves" choice {@link KillLootDeathEvents} makes for kill+loot.
 *
 * Drains into the *same* "events" upload key {@link KillLootDeathEvents} owns, so unlike that
 * class this one only ever appends to whatever's already there rather than overwriting it -
 * {@link #consumeState} must run after {@link KillLootDeathEvents#consumeState} in
 * {@link DataManager#submitToApi()}. Must only be touched from the client thread.
 */
public class RaidCompletionEvents {
    private static final long CORRELATION_WINDOW_MILLIS = 30_000;

    /** One of "mode" (CoX/ToB - {@code mode} may be {@code null} for regular difficulty) or
     * "level" (ToA numeric invocation level). */
    public static final class RaidDifficulty {
        final String kind;
        final String mode;
        final int level;

        private RaidDifficulty(String kind, String mode, int level) {
            this.kind = kind;
            this.mode = mode;
            this.level = level;
        }

        public static RaidDifficulty mode(String mode) {
            return new RaidDifficulty("mode", mode, 0);
        }

        public static RaidDifficulty level(int level) {
            return new RaidDifficulty("level", null, level);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("kind", kind);
            if ("mode".equals(kind)) {
                if (mode != null) {
                    map.put("mode", mode);
                }
            } else {
                map.put("level", level);
            }
            return map;
        }
    }

    private static final class PendingCompletion {
        final RaidDifficulty difficulty;
        final long detectedAtMillis;

        PendingCompletion(RaidDifficulty difficulty) {
            this.difficulty = difficulty;
            this.detectedAtMillis = System.currentTimeMillis();
        }

        boolean isFresh() {
            return System.currentTimeMillis() - detectedAtMillis <= CORRELATION_WINDOW_MILLIS;
        }
    }

    private PendingCompletion pendingCox;
    private PendingCompletion pendingToa;
    private final List<Map<String, Object>> pendingCompletions = new ArrayList<>();
    private String owner;

    private List<Map<String, Object>> consumedCompletions;
    private String consumedOwner;

    /** Called from {@code onChatMessage} once a CoX/ToA completion line is matched. */
    public synchronized void onChatCompletion(String raidType, RaidDifficulty difficulty) {
        if ("cox".equals(raidType)) {
            pendingCox = new PendingCompletion(difficulty);
        } else if ("toa".equals(raidType)) {
            pendingToa = new PendingCompletion(difficulty);
        }
    }

    /**
     * Called for every {@code LootReceived} whose source name is one of the three raid chests
     * (checked by the caller before {@link ChestLootSourceNames}'s broader chest handling, so
     * raid chests never also reach the standalone chest/clue "loot" event path).
     *
     * @return true if this loot was claimed as a raid completion (caller should also skip the
     * standalone notable-drop check for it, so the same gold isn't reported twice)
     */
    public synchronized boolean onRaidChestLoot(String playerName, String sourceName,
                                                 int worldX, int worldY, int plane, int world,
                                                 List<Map<String, Object>> items) {
        String raidType;
        RaidDifficulty difficulty;
        if ("Theatre of Blood".equals(sourceName)) {
            // ToB prints no chat-completion line at all - the reward chest loot IS the
            // completion signal. Mode is left unset ("regular") since it can't be told apart
            // from Hard Mode without widget parsing this feature doesn't otherwise need -
            // TODO: revisit if ToB Hard Mode distinction turns out to matter to users.
            raidType = "tob";
            difficulty = RaidDifficulty.mode(null);
        } else if ("Chambers of Xeric".equals(sourceName) && pendingCox != null && pendingCox.isFresh()) {
            raidType = "cox";
            difficulty = pendingCox.difficulty;
            pendingCox = null;
        } else if ("Tombs of Amascut".equals(sourceName) && pendingToa != null && pendingToa.isFresh()) {
            raidType = "toa";
            difficulty = pendingToa.difficulty;
            pendingToa = null;
        } else {
            // Not a raid chest, or the chat signal never arrived (or expired) - not our loot to
            // claim; caller falls back to its normal chest/notable-drop handling for this event.
            return false;
        }

        owner = playerName;
        Map<String, Object> event = new HashMap<>();
        event.put("type", "raid");
        event.put("raidType", raidType);
        event.put("difficulty", difficulty.toMap());
        event.put("worldX", worldX);
        event.put("worldY", worldY);
        event.put("plane", plane);
        event.put("world", world);
        event.put("occurredAt", Instant.now().toString());
        event.put("loot", items);
        pendingCompletions.add(event);
        return true;
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pendingCompletions.isEmpty()) return;

        String whoIsUpdating = (String) output.get("name");
        if (owner != null && owner.equals(whoIsUpdating)) {
            // Must append to KillLootDeathEvents' "events" list, not overwrite it - see class doc.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events =
                    (List<Map<String, Object>>) output.computeIfAbsent("events", k -> new ArrayList<Map<String, Object>>());
            events.addAll(pendingCompletions);
        }

        consumedCompletions = new ArrayList<>(pendingCompletions);
        consumedOwner = owner;
        pendingCompletions.clear();
        owner = null;
    }

    public synchronized void restoreState() {
        if (consumedCompletions == null) return;

        pendingCompletions.addAll(0, consumedCompletions);
        if (owner == null) {
            owner = consumedOwner;
        }
        consumedCompletions = null;
        consumedOwner = null;
    }
}
