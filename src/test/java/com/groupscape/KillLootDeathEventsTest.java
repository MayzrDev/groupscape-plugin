package com.groupscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class KillLootDeathEventsTest {
    @Test
    public void consumeStateAttachesEventsWhenOwnerMatches() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath");

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue(output.containsKey("events"));
        assertEquals(1, ((List<?>) output.get("events")).size());
    }

    @Test
    public void consumeStateRetriesInsteadOfDroppingOnOwnerMismatch() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath");

        // A flush for a different member's snapshot arrives first (e.g. a transient
        // local-player-name hiccup around a death/teleport) - the death must not be dropped.
        Map<String, Object> mismatched = new HashMap<>();
        mismatched.put("name", "SomeoneElse");
        events.consumeState(mismatched);

        assertFalse("event must not be attached to a mismatched flush", mismatched.containsKey("events"));

        // The next flush targeting the real owner must still be able to claim it.
        Map<String, Object> matched = new HashMap<>();
        matched.put("name", "Zezima");
        events.consumeState(matched);

        assertTrue("event must survive to be attached on a later matching flush", matched.containsKey("events"));
        assertEquals(1, ((List<?>) matched.get("events")).size());
    }
}
