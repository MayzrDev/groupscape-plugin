package com.groupscape;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RaidCompletionEventsTest {
    private static List<Map<String, Object>> emptyLoot() {
        return new ArrayList<>();
    }

    @Test
    public void chatCompletionFollowedByMatchingChestLootQueuesOneEvent() {
        RaidCompletionEvents events = new RaidCompletionEvents();
        events.onChatCompletion("cox", RaidCompletionEvents.RaidDifficulty.mode(null));

        boolean claimed = events.onRaidChestLoot(
                "Woox", "Chambers of Xeric", 100, 200, 0, 301, emptyLoot());

        assertTrue(claimed);
        Map<String, Object> output = new HashMap<>();
        output.put("name", "Woox");
        events.consumeState(output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queued = (List<Map<String, Object>>) output.get("events");
        assertEquals(1, queued.size());
        assertEquals("raid", queued.get(0).get("type"));
        assertEquals("cox", queued.get(0).get("raidType"));
    }

    @Test
    public void tobChestLootIsClaimedWithoutAnyChatSignal() {
        RaidCompletionEvents events = new RaidCompletionEvents();

        boolean claimed = events.onRaidChestLoot(
                "Woox", "Theatre of Blood", 100, 200, 0, 301, emptyLoot());

        assertTrue(claimed);
    }

    @Test
    public void chestLootWithNoMatchingChatSignalIsStillClaimedWithUnsetDifficulty() {
        RaidCompletionEvents events = new RaidCompletionEvents();
        // No onChatCompletion("cox", ...) call this time - chat line missed, expired, or the
        // pattern failed to match a real completion message. Chest loot is still authoritative.

        boolean claimed = events.onRaidChestLoot(
                "Woox", "Chambers of Xeric", 100, 200, 0, 301, emptyLoot());

        assertTrue(claimed);
        Map<String, Object> output = new HashMap<>();
        output.put("name", "Woox");
        events.consumeState(output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queued = (List<Map<String, Object>>) output.get("events");
        assertEquals(1, queued.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> difficulty = (Map<String, Object>) queued.get(0).get("difficulty");
        assertEquals("mode", difficulty.get("kind"));
        assertFalse(difficulty.containsKey("mode"));
    }

    @Test
    public void toaChestLootWithNoMatchingChatSignalHasUnresolvedLevel() {
        RaidCompletionEvents events = new RaidCompletionEvents();

        boolean claimed = events.onRaidChestLoot(
                "Woox", "Tombs of Amascut", 100, 200, 0, 301, emptyLoot());

        assertTrue(claimed);
        Map<String, Object> output = new HashMap<>();
        output.put("name", "Woox");
        events.consumeState(output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queued = (List<Map<String, Object>>) output.get("events");
        @SuppressWarnings("unchecked")
        Map<String, Object> difficulty = (Map<String, Object>) queued.get(0).get("difficulty");
        assertEquals("level", difficulty.get("kind"));
        assertEquals(-1, difficulty.get("level"));
    }

    @Test
    public void unrelatedChestNameIsNeverClaimed() {
        RaidCompletionEvents events = new RaidCompletionEvents();
        events.onChatCompletion("cox", RaidCompletionEvents.RaidDifficulty.mode(null));

        boolean claimed = events.onRaidChestLoot(
                "Woox", "Barrows", 100, 200, 0, 301, emptyLoot());

        assertFalse(claimed);
    }

    @Test
    public void appendsToExistingEventsListRatherThanOverwritingIt() {
        RaidCompletionEvents raidEvents = new RaidCompletionEvents();
        raidEvents.onChatCompletion("toa", RaidCompletionEvents.RaidDifficulty.level(300));
        raidEvents.onRaidChestLoot("Woox", "Tombs of Amascut", 1, 2, 0, 301, emptyLoot());

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Woox");
        List<Map<String, Object>> existing = new ArrayList<>();
        Map<String, Object> priorKill = new HashMap<>();
        priorKill.put("type", "kill");
        existing.add(priorKill);
        output.put("events", existing);

        raidEvents.consumeState(output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) output.get("events");
        assertEquals(2, events.size());
        assertEquals("kill", events.get(0).get("type"));
        assertEquals("raid", events.get(1).get("type"));
    }
}
