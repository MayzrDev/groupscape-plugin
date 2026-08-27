package com.groupscape.sidepanel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Prayer;
import net.runelite.api.SpriteID;

/**
 * Prayer -&gt; prayer-tab sprite id, duplicated from {@code PartyFrameOverlay}'s
 * {@code buildPrayerSpriteIds()} (same explicit mapping, most names line up but at least one
 * Ruinous Powers curse doesn't) so the sidepanel doesn't need a cross-package dependency on the
 * overlay for a static lookup table.
 */
final class PrayerSprites {
    static final Map<Prayer, Integer> SPRITE_IDS = build();
    static final Set<Prayer> OVERHEAD = EnumSet.of(
            Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MAGIC,
            Prayer.RETRIBUTION, Prayer.REDEMPTION, Prayer.SMITE
    );

    private PrayerSprites() {
    }

    private static Map<Prayer, Integer> build() {
        Map<Prayer, Integer> ids = new EnumMap<>(Prayer.class);
        ids.put(Prayer.THICK_SKIN, SpriteID.PRAYER_THICK_SKIN);
        ids.put(Prayer.BURST_OF_STRENGTH, SpriteID.PRAYER_BURST_OF_STRENGTH);
        ids.put(Prayer.CLARITY_OF_THOUGHT, SpriteID.PRAYER_CLARITY_OF_THOUGHT);
        ids.put(Prayer.SHARP_EYE, SpriteID.PRAYER_SHARP_EYE);
        ids.put(Prayer.MYSTIC_WILL, SpriteID.PRAYER_MYSTIC_WILL);
        ids.put(Prayer.ROCK_SKIN, SpriteID.PRAYER_ROCK_SKIN);
        ids.put(Prayer.SUPERHUMAN_STRENGTH, SpriteID.PRAYER_SUPERHUMAN_STRENGTH);
        ids.put(Prayer.IMPROVED_REFLEXES, SpriteID.PRAYER_IMPROVED_REFLEXES);
        ids.put(Prayer.RAPID_RESTORE, SpriteID.PRAYER_RAPID_RESTORE);
        ids.put(Prayer.RAPID_HEAL, SpriteID.PRAYER_RAPID_HEAL);
        ids.put(Prayer.PROTECT_ITEM, SpriteID.PRAYER_PROTECT_ITEM);
        ids.put(Prayer.HAWK_EYE, SpriteID.PRAYER_HAWK_EYE);
        ids.put(Prayer.MYSTIC_LORE, SpriteID.PRAYER_MYSTIC_LORE);
        ids.put(Prayer.STEEL_SKIN, SpriteID.PRAYER_STEEL_SKIN);
        ids.put(Prayer.ULTIMATE_STRENGTH, SpriteID.PRAYER_ULTIMATE_STRENGTH);
        ids.put(Prayer.INCREDIBLE_REFLEXES, SpriteID.PRAYER_INCREDIBLE_REFLEXES);
        ids.put(Prayer.PROTECT_FROM_MAGIC, SpriteID.PRAYER_PROTECT_FROM_MAGIC);
        ids.put(Prayer.PROTECT_FROM_MISSILES, SpriteID.PRAYER_PROTECT_FROM_MISSILES);
        ids.put(Prayer.PROTECT_FROM_MELEE, SpriteID.PRAYER_PROTECT_FROM_MELEE);
        ids.put(Prayer.EAGLE_EYE, SpriteID.PRAYER_EAGLE_EYE);
        ids.put(Prayer.MYSTIC_MIGHT, SpriteID.PRAYER_MYSTIC_MIGHT);
        ids.put(Prayer.RETRIBUTION, SpriteID.PRAYER_RETRIBUTION);
        ids.put(Prayer.REDEMPTION, SpriteID.PRAYER_REDEMPTION);
        ids.put(Prayer.SMITE, SpriteID.PRAYER_SMITE);
        ids.put(Prayer.CHIVALRY, SpriteID.PRAYER_CHIVALRY);
        ids.put(Prayer.DEADEYE, SpriteID.PRAYER_DEADEYE);
        ids.put(Prayer.MYSTIC_VIGOUR, SpriteID.PRAYER_MYSTIC_VIGOUR);
        ids.put(Prayer.PIETY, SpriteID.PRAYER_PIETY);
        ids.put(Prayer.PRESERVE, SpriteID.PRAYER_PRESERVE);
        ids.put(Prayer.RIGOUR, SpriteID.PRAYER_RIGOUR);
        ids.put(Prayer.AUGURY, SpriteID.PRAYER_AUGURY);
        ids.put(Prayer.RP_REJUVENATION, SpriteID.PRAYER_RP_REJUVENATION);
        ids.put(Prayer.RP_ANCIENT_STRENGTH, SpriteID.PRAYER_RP_ANCIENT_STRENGTH);
        ids.put(Prayer.RP_ANCIENT_SIGHT, SpriteID.PRAYER_RP_ANCIENT_SIGHT);
        ids.put(Prayer.RP_ANCIENT_WILL, SpriteID.PRAYER_RP_ANCIENT_WILL);
        ids.put(Prayer.RP_PROTECT_ITEM, SpriteID.PRAYER_RP_PROTECT_ITEM);
        ids.put(Prayer.RP_RUINOUS_GRACE, SpriteID.PRAYER_RP_RUINOUS_GRACE);
        ids.put(Prayer.RP_DAMPEN_MAGIC, SpriteID.PRAYER_RP_DAMPEN_MAGIC);
        ids.put(Prayer.RP_DAMPEN_RANGED, SpriteID.PRAYER_RP_DAMPEN_RANGED);
        ids.put(Prayer.RP_DAMPEN_MELEE, SpriteID.PRAYER_RP_DAMPEN_MELEE);
        ids.put(Prayer.RP_TRINITAS, SpriteID.PRAYER_RP_TRINITAS);
        ids.put(Prayer.RP_BERSERKER, SpriteID.PRAYER_RP_BERSERKER);
        ids.put(Prayer.RP_PURGE, SpriteID.PRAYER_RP_PURGE);
        ids.put(Prayer.RP_METABOLISE, SpriteID.PRAYER_RP_METABOLISE);
        ids.put(Prayer.RP_REBUKE, SpriteID.PRAYER_RP_REBUKE);
        ids.put(Prayer.RP_VINDICATION, SpriteID.PRAYER_RP_VINDICATION);
        ids.put(Prayer.RP_DECIMATE, SpriteID.PRAYER_RP_DECIMATE);
        ids.put(Prayer.RP_ANNIHILATE, SpriteID.PRAYER_RP_ANNIHILATE);
        ids.put(Prayer.RP_VAPORISE, SpriteID.PRAYER_RP_VAPORISE);
        ids.put(Prayer.RP_FUMUS_VOW, SpriteID.PRAYER_RP_FUMUS_VOW);
        ids.put(Prayer.RP_UMBRA_VOW, SpriteID.PRAYER_RP_UMBRAS_VOW);
        ids.put(Prayer.RP_CRUORS_VOW, SpriteID.PRAYER_RP_CRUORS_VOW);
        ids.put(Prayer.RP_GLACIES_VOW, SpriteID.PRAYER_RP_GLACIES_VOW);
        ids.put(Prayer.RP_WRATH, SpriteID.PRAYER_RP_WRATH);
        ids.put(Prayer.RP_INTENSIFY, SpriteID.PRAYER_RP_INTENSIFY);
        return ids;
    }

    static Prayer parse(String name) {
        try {
            return Prayer.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
