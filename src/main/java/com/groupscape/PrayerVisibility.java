package com.groupscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Prayer;

/**
 * Filters a raw {@code client.isPrayerActive()} scan down to the prayers that should actually be
 * shown. RuneLite's per-prayer varbits don't cleanly separate a base prayer from the curse/upgrade
 * that replaces it in the prayer book (Deadeye over Rigour/Eagle Eye/Hawk Eye/Sharp Eye, Mystic
 * Vigour over Augury/Mystic Might/Mystic Lore/Mystic Will): activating the upgrade reports every
 * prayer in that book column as active at once. Drop a base prayer whenever its listed upgrade is
 * also present in the same raw scan.
 */
public final class PrayerVisibility {
    private static final Map<Prayer, Prayer> BASE_PRAYER_SUPPRESSED_BY_UPGRADE = new EnumMap<>(Prayer.class);

    static {
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.RIGOUR, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.EAGLE_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.HAWK_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.SHARP_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.AUGURY, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_MIGHT, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_LORE, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_WILL, Prayer.MYSTIC_VIGOUR);
    }

    private PrayerVisibility() {
    }

    public static List<String> visible(List<String> rawPrayerNames) {
        if (rawPrayerNames == null || rawPrayerNames.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Prayer> active = EnumSet.noneOf(Prayer.class);
        for (String name : rawPrayerNames) {
            Prayer prayer = parsePrayer(name);
            if (prayer != null) {
                active.add(prayer);
            }
        }

        List<String> visible = new ArrayList<>(rawPrayerNames.size());
        for (String name : rawPrayerNames) {
            Prayer prayer = parsePrayer(name);
            Prayer upgrade = prayer != null ? BASE_PRAYER_SUPPRESSED_BY_UPGRADE.get(prayer) : null;
            if (upgrade != null && active.contains(upgrade)) {
                continue;
            }
            visible.add(name);
        }
        return visible;
    }

    private static Prayer parsePrayer(String prayerName) {
        try {
            return Prayer.valueOf(prayerName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
