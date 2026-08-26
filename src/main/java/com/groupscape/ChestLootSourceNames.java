package com.groupscape;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Curated allowlist of chest/instance reward source names, matched against
 * {@code LootReceived.getName()} when {@code getType() == LootRecordType.EVENT} - these are
 * never correlated to an NPC kill (there's often no {@code NpcDespawned} to hook at all, e.g.
 * raids), unlike {@link BossKillNpcNames}'s kill-detection list.
 *
 * Kept in sync with the server's chest-loot allowlist (server/src/loot_sources.rs).
 */
public final class ChestLootSourceNames {
    private ChestLootSourceNames() {
    }

    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
            "Chambers of Xeric",
            "Theatre of Blood",
            "Tombs of Amascut",
            "Barrows",
            "The Gauntlet",
            "The Corrupted Gauntlet",
            "Wintertodt",
            "Tempoross",
            "Zalcano",
            "Guardians of the Rift",
            "Fortis Colosseum"
    ));

    public static boolean isTrackedChest(String sourceName) {
        return sourceName != null && NAMES.contains(sourceName);
    }
}
