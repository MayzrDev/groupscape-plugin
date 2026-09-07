package com.groupscape;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Curated allowlist of chest/instance reward source names, matched against
 * {@code LootReceived.getName()} when {@code getType() == LootRecordType.EVENT} - these are
 * never correlated to an NPC kill (there's often no {@code NpcDespawned} to hook at all, e.g.
 * raids), unlike the {@code NpcDespawned}-based kill-detection in
 * {@link com.groupscape.GroupScapeTrackerPlugin#onNpcDespawned}.
 *
 * Doesn't include the raid chests ({@code RAID_CHEST_NAMES} in
 * {@link com.groupscape.GroupScapeTrackerPlugin}) or the combo chests ({@link ComboKillEvents}) -
 * both are checked ahead of this class in {@code onLootReceived} and claimed by their own
 * dedicated handling instead.
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
            "The Gauntlet",
            "The Corrupted Gauntlet",
            "Wintertodt",
            "Tempoross",
            "Zalcano",
            "Guardians of the Rift",
            "Fortis Colosseum",
            "Hunters' loot sack (basic)",
            "Hunters' loot sack (adept)",
            "Hunters' loot sack (expert)",
            "Hunters' loot sack (master)"
    ));

    public static boolean isTrackedChest(String sourceName) {
        return sourceName != null && NAMES.contains(sourceName);
    }
}
