package com.groupscape;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.VarbitID;

public class ActivePrayersState implements ConsumableState {
    private final List<String> activePrayers;
    private final transient String playerName;

    ActivePrayersState(String playerName, Client client) {
        this.playerName = playerName;
        this.activePrayers = new ArrayList<>();

        // client.isPrayerActive() doesn't distinguish Eagle Eye/Deadeye or Mystic Might/Mystic
        // Vigour: unlocking the upgrade replaces the base prayer in the prayer book but the
        // client still reports the base prayer's varbit as active too. Mirror RuneLite's own
        // PrayerType.java gating so we only report the prayer actually shown in the book.
        boolean inLms = client.getVarbitValue(VarbitID.BR_INGAME) != 0;
        boolean deadeyeUnlocked = !inLms && client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0;
        boolean mysticVigourUnlocked = !inLms && client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0;

        for (Prayer prayer : Prayer.values()) {
            if (!client.isPrayerActive(prayer)) {
                continue;
            }
            if (prayer == Prayer.EAGLE_EYE && deadeyeUnlocked) {
                continue;
            }
            if (prayer == Prayer.MYSTIC_MIGHT && mysticVigourUnlocked) {
                continue;
            }
            activePrayers.add(prayer.name());
        }
    }

    @Override
    public Object get() {
        return activePrayers;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ActivePrayersState)) return false;

        ActivePrayersState other = (ActivePrayersState) o;
        return other.activePrayers.equals(activePrayers);
    }
}
