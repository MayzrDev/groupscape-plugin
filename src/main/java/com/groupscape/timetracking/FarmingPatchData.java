package com.groupscape.timetracking;

import net.runelite.api.gameval.VarbitID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Herb and Tree patch locations, ported from RuneLite's own
 * {@code net.runelite.client.plugins.timetracking.farming.FarmingWorld} - that class (and the
 * {@code FarmingPatch}/{@code FarmingRegion} types it builds) are package-private in RuneLite's
 * own source, so GroupScape can't reference them at compile time even though they're on the
 * runtime classpath. Only Herb and Tree patches are ported for v1 (see plan); other patch types
 * (allotment, flower, fruit tree, hops, bush, grape, special) are explicit follow-up work.
 *
 * <p>Each entry's {@code regionId} is the <em>primary</em> region RuneLite constructed the patch
 * with (some patches are also visible/tracked from alias regions, but the Time Tracking config
 * key always uses the primary region's ID - see {@code FarmingPatch.configKey()}).
 */
public final class FarmingPatchData {
    public enum Kind { HERB, TREE }

    public static final class Entry {
        public final String label;
        public final int regionId;
        public final int varbitId;
        public final Kind kind;

        Entry(String label, int regionId, int varbitId, Kind kind) {
            this.label = label;
            this.regionId = regionId;
            this.varbitId = varbitId;
            this.kind = kind;
        }

        /** Matches {@code FarmingPatch.configKey()}: {@code "<regionID>.<varbitID>"}. */
        public String configKey() {
            return regionId + "." + varbitId;
        }
    }

    private static final int FARMING_TRANSMIT_A = VarbitID.FARMING_TRANSMIT_A;
    private static final int FARMING_TRANSMIT_B = VarbitID.FARMING_TRANSMIT_B;
    private static final int FARMING_TRANSMIT_D = VarbitID.FARMING_TRANSMIT_D;
    private static final int FARMING_TRANSMIT_E = VarbitID.FARMING_TRANSMIT_E;
    private static final int FARMING_TRANSMIT_G = VarbitID.FARMING_TRANSMIT_G;

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(build());

    private static List<Entry> build() {
        List<Entry> entries = new ArrayList<>();

        // Herb patches
        entries.add(new Entry("Ardougne herb patch", 10548, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Catherby herb patch", 11062, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Civitas illa Fortis herb patch", 6192, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Falador herb patch", 12083, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Harmony herb patch", 15148, FARMING_TRANSMIT_B, Kind.HERB));
        entries.add(new Entry("Kourend herb patch", 6967, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Morytania herb patch", 14391, FARMING_TRANSMIT_D, Kind.HERB));
        entries.add(new Entry("Troll Stronghold herb patch", 11321, FARMING_TRANSMIT_A, Kind.HERB));
        entries.add(new Entry("Weiss herb patch", 11325, FARMING_TRANSMIT_A, Kind.HERB));
        entries.add(new Entry("Farming Guild herb patch", 4922, FARMING_TRANSMIT_E, Kind.HERB));

        // Tree patches
        entries.add(new Entry("Falador tree patch", 11828, FARMING_TRANSMIT_A, Kind.TREE));
        entries.add(new Entry("Gnome Stronghold tree patch", 9781, FARMING_TRANSMIT_A, Kind.TREE));
        entries.add(new Entry("Lumbridge tree patch", 12594, FARMING_TRANSMIT_A, Kind.TREE));
        entries.add(new Entry("Taverley tree patch", 11573, FARMING_TRANSMIT_A, Kind.TREE));
        entries.add(new Entry("Varrock tree patch", 12854, FARMING_TRANSMIT_A, Kind.TREE));
        entries.add(new Entry("Farming Guild tree patch", 4922, FARMING_TRANSMIT_G, Kind.TREE));

        return entries;
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private FarmingPatchData() {
    }
}
