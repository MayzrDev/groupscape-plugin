package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
public class CollectionLogV2Manager {
    // item id -> quantity, resolved via the live drop-detection path (chat message + inventory/
    // loot match) - these are genuine "this just happened" unlocks and go out under
    // collection_log_v2, which the server treats as notify-worthy.
    private final Map<Integer, Integer> liveItems = new HashMap<>();
    // item id -> quantity, seen only via a collection log widget scan with no matching live
    // signal (e.g. the player just opened their log to browse) - these go out under
    // collection_log_sync, which the server merges into stored state without firing Discord/
    // activity-feed events, since they aren't drops happening now.
    private final Map<Integer, Integer> syncItems = new HashMap<>();
    // item ids registered via the live path since the last clear - lets a scan that follows a
    // live drop (to learn the true stackable quantity) correct liveItems instead of being
    // mistaken for a plain sync hit.
    private final java.util.Set<Integer> confirmedLiveIds = new java.util.HashSet<>();

    public synchronized void storeLiveClogItem(int itemId, int quantity) {
        if (quantity <= 0) return;
        liveItems.put(itemId, quantity);
        confirmedLiveIds.add(itemId);
    }

    public synchronized void storeScannedClogItem(int itemId, int quantity) {
        if (quantity <= 0) return;
        if (confirmedLiveIds.contains(itemId)) {
            liveItems.put(itemId, quantity);
        } else {
            syncItems.put(itemId, quantity);
        }
    }

    public synchronized void consumeClogItems(Map<String, Object> updates) {
        if (!liveItems.isEmpty()) {
            updates.put("collection_log_v2", flatten(liveItems));
        }
        if (!syncItems.isEmpty()) {
            updates.put("collection_log_sync", flatten(syncItems));
        }
    }

    private static List<Integer> flatten(Map<Integer, Integer> items) {
        List<Integer> result = new ArrayList<>(items.size() * 2);
        for (Map.Entry<Integer, Integer> item : items.entrySet()) {
            result.add(item.getKey());
            result.add(item.getValue());
        }
        return result;
    }

    public synchronized void clearClogItems() {
        liveItems.clear();
        syncItems.clear();
        confirmedLiveIds.clear();
    }

    @Subscribe
    public synchronized void onGameStateChanged(GameStateChanged ev) {
        if (ev.getGameState() != GameState.LOGGED_IN) {
            clearClogItems();
        }
    }
}
