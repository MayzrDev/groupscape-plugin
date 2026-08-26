package com.groupscape;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Curated NPC-name allowlist for kill detection, ported from groupscape-old: there is no
 * generic "NPC died" event in the RuneLite API (confirmed against RuneLite's own NpcUtil,
 * which hand-maintains a per-boss-ID exception table for exactly this reason). Kill
 * detection here is "name in this list + despawned at 0 health ratio", which deliberately
 * doesn't special-case gargoyles/rockslugs (die above 0hp), KQ/Vet'ion (mid-fight
 * transforms), or Amoxliatl (non-standard health bar) - those need per-NPC-ID logic this
 * pass doesn't add, flagged for later refinement against a live client. Dual-NPC encounters
 * (Dusk/Dawn, Eldric the Ice King/Verak Lith) aren't combined either - each half despawning
 * at 0 health fires its own kill event, so a full encounter clear logs as two entries rather
 * than one.
 *
 * Kept in sync with the server's notable-kill list (server/src/notable_npcs.rs) for
 * repeatable, single/dual-NPC-despawn bosses; that list also carries raid activities
 * (ToB/CoX/ToA/Barrows) and one-off quest bosses that have no NpcDespawned signal to hook,
 * so it's intentionally a superset of this one.
 */
public final class BossKillNpcNames {
    private BossKillNpcNames() {
    }

    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
            "Zulrah",
            "Vorkath",
            "The Corrupted Gauntlet",
            "Crystalline Hunllef",
            "Corrupted Hunllef",
            "Kraken",
            "Cerberus",
            "General Graardor",
            "Commander Zilyana",
            "K'ril Tsutsaroth",
            "Kree'arra",
            "Kalphite Queen",
            "King Black Dragon",
            "Giant Mole",
            "Abyssal Sire",
            "Alchemical Hydra",
            "Thermonuclear Smoke Devil",
            "Vet'ion",
            "Callisto",
            "Venenatis",
            "Chaos Elemental",
            "Scorpia",
            "Dharok the Wretched",
            "Ahrim the Blighted",
            "Karil the Tainted",
            "Guthan the Infested",
            "Torag the Corrupted",
            "Verac the Defiled",
            "Wintertodt",
            "Tempoross",
            "Zalcano",
            "Nightmare of Ashihama",
            "Nex",
            "Phantom Muspah",
            "Corporeal Beast",
            "TzTok-Jad",
            "TzKal-Zuk",
            "Sarachnis",
            "Skotizo",
            "Obor",
            "Bryophyta",
            "Dagannoth Rex",
            "Dagannoth Prime",
            "Dagannoth Supreme",
            "Dusk",
            "Dawn",
            "Duke Sucellus",
            "The Leviathan",
            "The Whisperer",
            "Vardorvis",
            "The Hueycoatl",
            "Yama",
            "Araxxor",
            "Eldric the Ice King",
            "Verak Lith"
    ));

    public static boolean isTrackedBoss(String npcName) {
        return npcName != null && NAMES.contains(npcName);
    }
}
