package com.groupscape;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Prayer;

public class ActivePrayersState implements ConsumableState {
    private final List<String> activePrayers;
    private final transient String playerName;

    ActivePrayersState(String playerName, Client client) {
        this.playerName = playerName;
        this.activePrayers = new ArrayList<>();
        for (Prayer prayer : Prayer.values()) {
            if (client.isPrayerActive(prayer)) {
                activePrayers.add(prayer.name());
            }
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
