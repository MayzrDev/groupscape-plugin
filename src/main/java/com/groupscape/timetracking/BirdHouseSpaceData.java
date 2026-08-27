package com.groupscape.timetracking;

import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The four Fossil Island bird house spaces, ported from RuneLite's own
 * {@code net.runelite.client.plugins.timetracking.hunter.BirdHouseSpace} - that enum is
 * package-private in RuneLite's own source. Unlike farming, birdhouse completion is a flat
 * duration from the observed timestamp (see {@link #DURATION_SECONDS}), not tick-rate/stage
 * based, so there's no equivalent of {@code TickOffsetTracker} needed here.
 */
public final class BirdHouseSpaceData {
    /** Matches RuneLite's own {@code BirdHouseTracker.BIRD_HOUSE_DURATION} (50 minutes). */
    public static final long DURATION_SECONDS = 50 * 60;

    public static final class Entry {
        public final String label;
        public final int varp;

        Entry(String label, int varp) {
            this.label = label;
            this.varp = varp;
        }

        /** Matches {@code BirdHouseTracker}'s stored key: {@code "birdhouse.<varp>"}. */
        public String configKey() {
            return TimeTrackingConfig.BIRD_HOUSE + "." + varp;
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("Mushroom Meadow (North)", VarPlayerID.BIRDHOUSE_TRANSMIT_A),
            new Entry("Mushroom Meadow (South)", VarPlayerID.BIRDHOUSE_TRANSMIT_B),
            new Entry("Verdant Valley (Northeast)", VarPlayerID.BIRDHOUSE_TRANSMIT_C),
            new Entry("Verdant Valley (Southwest)", VarPlayerID.BIRDHOUSE_TRANSMIT_D)
    ));

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * Mirrors {@code BirdHouseState.fromVarpValue()}: {@code 0} = empty, a multiple of 3 = seeded
     * (growing - has a completion time), anything else = built but not yet seeded (no timer).
     */
    public static boolean isSeeded(int varp) {
        return varp > 0 && varp % 3 == 0;
    }

    private BirdHouseSpaceData() {
    }
}
