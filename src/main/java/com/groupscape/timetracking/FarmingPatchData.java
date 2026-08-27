package com.groupscape.timetracking;

import net.runelite.client.plugins.timetracking.farming.PatchImplementation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Herb, regular tree, fruit tree, and hardwood tree patch locations, sourced from
 * {@link FarmingWorldReflector} (RuneLite's own real patch table, read via reflection) rather than
 * hand-typed. Only these four patch kinds are surfaced for v1; other Time Tracking patch types
 * (allotment, flower, hops, bush, grape, spirit tree, special, etc.) are explicit follow-up work.
 */
public final class FarmingPatchData {
    public enum Kind { HERB, TREE, FRUIT_TREE, HARDWOOD_TREE }

    private static final Map<PatchImplementation, Kind> SUPPORTED_KINDS = new EnumMap<>(PatchImplementation.class);

    static {
        SUPPORTED_KINDS.put(PatchImplementation.HERB, Kind.HERB);
        SUPPORTED_KINDS.put(PatchImplementation.TREE, Kind.TREE);
        SUPPORTED_KINDS.put(PatchImplementation.FRUIT_TREE, Kind.FRUIT_TREE);
        SUPPORTED_KINDS.put(PatchImplementation.HARDWOOD_TREE, Kind.HARDWOOD_TREE);
    }

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

    public static List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        for (FarmingWorldReflector.PatchEntry patch : FarmingWorldReflector.entries()) {
            Kind kind = SUPPORTED_KINDS.get(patch.kind);
            if (kind == null) continue;
            result.add(new Entry(patch.label, patch.regionId, patch.varbitId, kind));
        }
        return Collections.unmodifiableList(result);
    }

    private FarmingPatchData() {
    }
}
