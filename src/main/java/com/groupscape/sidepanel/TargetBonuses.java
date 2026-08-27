package com.groupscape.sidepanel;

import java.util.HashMap;
import java.util.Map;

/**
 * Undead (Salve amulet-style) and Slayer (Black mask/Slayer helmet-style) target bonuses, ported
 * from the website's public/data/target_bonuses.json - not in the wiki's per-item Infobox Bonuses
 * template {@link EquipmentBonusesPanel}'s {@code ItemEquipmentStats} numbers come from, so it's
 * a small hand-curated lookup rather than something derivable from RuneLite's item-stat cache.
 * Each item's percentage is its melee Attack/Strength bonus - imbued variants extend part of that
 * bonus to ranged/magic too, but never above the melee figure, so one flat number per item loses
 * nothing for a single-value display.
 */
final class TargetBonuses {
    private static final Map<Integer, Double> UNDEAD = new HashMap<>();
    private static final Map<Integer, Double> SLAYER = new HashMap<>();

    static {
        UNDEAD.put(4081, 16.67);
        UNDEAD.put(10588, 20.0);
        UNDEAD.put(12017, 16.67);
        UNDEAD.put(25250, 16.67);
        UNDEAD.put(26763, 16.67);
        UNDEAD.put(12018, 20.0);
        UNDEAD.put(25278, 20.0);
        UNDEAD.put(26782, 20.0);

        int[] slayerIds = {
                8901, 8903, 8905, 8907, 8909, 8911, 8913, 8915, 8917, 8919, 8921,
                11774, 11775, 11776, 11777, 11778, 11779, 11780, 11781, 11782, 11783, 11784,
                25266, 25267, 25268, 25269, 25270, 25271, 25272, 25273, 25274, 25275, 25276,
                26771, 26772, 26773, 26774, 26775, 26776, 26777, 26778, 26779, 26780, 26781,
                11864, 11865, 25177, 26674,
        };
        for (int id : slayerIds) {
            SLAYER.put(id, 16.67);
        }
    }

    private TargetBonuses() {
    }

    /** Highest undead bonus among the given equipped item ids, or 0 if none qualify. */
    static double undeadPercent(Iterable<Integer> equippedItemIds) {
        return highest(UNDEAD, equippedItemIds);
    }

    /** Highest slayer bonus among the given equipped item ids, or 0 if none qualify. */
    static double slayerPercent(Iterable<Integer> equippedItemIds) {
        return highest(SLAYER, equippedItemIds);
    }

    private static double highest(Map<Integer, Double> table, Iterable<Integer> equippedItemIds) {
        double best = 0;
        for (int id : equippedItemIds) {
            Double value = table.get(id);
            if (value != null && value > best) {
                best = value;
            }
        }
        return best;
    }
}
