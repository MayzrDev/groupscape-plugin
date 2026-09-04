package com.groupscape;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class CollectionLogWidgetSubscriber {
    /** Fires the moment a new item is unlocked, whether or not the log UI is open - this is
     * what lets collection log sync happen at drop time instead of only on next log open. */
    private static final Pattern NEW_CLOG_ITEM_PATTERN =
            Pattern.compile("^New item added to your collection log: (?<item>.+)$");

    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private CollectionLogV2Manager collectionLogV2Manager;

    private boolean searchTriggered = false;
    private int searchTriggeredTick = -1;

    // itemManager.search() only matches items with a GE price, so it silently misses a large
    // share of collection log items (untradeable, or the search cache just hasn't loaded yet).
    // Instead, resolve the item id from the player's own inventory: snapshot it the moment the
    // unlock chat message fires, then diff it against the inventory a tick later (once the item
    // has actually landed) and match the newly-added item's real name (read straight from the
    // local item cache, not a name search) against the chat message's item name. This is the
    // same approach the reference collection-log RuneLite plugin uses and works for every item
    // regardless of GE tradeability.
    //
    // The inventory-diff alone races against event ordering: for some drops (e.g. boss uniques)
    // the item lands in the inventory the same tick the unlock message is queued, and RuneLite
    // can dispatch ItemContainerChanged before ChatMessage - the diff then sees no change to
    // match against. LootReceived is a second, independent source for the same item (the raw
    // loot the kill granted) that isn't subject to that same ordering, so it's kept as a
    // fallback resolver rather than clearing pendingItemName on a failed diff.
    private String pendingItemName;
    private Map<Integer, Integer> inventorySnapshot;
    private int pendingItemTick = -1;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        GameState s = e.getGameState();
        if (s != GameState.HOPPING && s != GameState.LOGGED_IN) {
            searchTriggered = false;
            searchTriggeredTick = -1;
            pendingItemName = null;
            inventorySnapshot = null;
            pendingItemTick = -1;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (searchTriggeredTick != -1) {
            int currentTick = client.getTickCount();

            if (currentTick - searchTriggeredTick >= 500) {
                searchTriggered = false;
                searchTriggeredTick = -1;
            }
        }

        // Neither the inventory diff nor LootReceived resolved the pending item within a few
        // ticks - give up and fall back to the next manual/widget-triggered collection log scan
        // (onScriptPreFired below) rather than letting a stale name linger and get matched
        // against an unrelated later drop.
        if (pendingItemTick != -1 && client.getTickCount() - pendingItemTick >= 5) {
            log.warn("timed out resolving collection log item id for name '{}'", pendingItemName);
            pendingItemName = null;
            inventorySnapshot = null;
            pendingItemTick = -1;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String message = Text.removeTags(event.getMessage());
        Matcher m = NEW_CLOG_ITEM_PATTERN.matcher(message);
        if (!m.find()) return;

        pendingItemName = m.group("item").trim();
        inventorySnapshot = snapshotInventory();
        pendingItemTick = client.getTickCount();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (pendingItemName == null) return;
        if (event.getContainerId() != InventoryID.INV) return;

        Map<Integer, Integer> before = inventorySnapshot != null ? inventorySnapshot : new HashMap<>();
        Map<Integer, Integer> after = snapshotInventory();

        int resolvedItemId = -1;
        for (Map.Entry<Integer, Integer> entry : after.entrySet()) {
            int itemId = entry.getKey();
            int gained = entry.getValue() - before.getOrDefault(itemId, 0);
            if (gained <= 0) continue;

            ItemComposition composition = itemManager.getItemComposition(itemId);
            if (composition.getName().equalsIgnoreCase(pendingItemName)) {
                resolvedItemId = itemId;
                break;
            }
        }

        if (resolvedItemId == -1) {
            // No match yet - some rewards (e.g. clue caskets) route through a different
            // container before landing in the inventory, or this ItemContainerChanged simply
            // fired before the item actually landed. Leave pendingItemName set so
            // onLootReceived (or a later ItemContainerChanged) still gets a chance to resolve
            // it; onGameTick above expires it if nothing ever does.
            return;
        }

        // Real quantity (for stackable log entries) is only known once the log widget is
        // scanned; this just registers the unlock immediately so it isn't lost until the
        // player next opens the log. onScriptPreFired below overwrites it with the true count,
        // while keeping it flagged as a live drop rather than a plain sync hit.
        collectionLogV2Manager.storeLiveClogItem(resolvedItemId, 1);
        pendingItemName = null;
        inventorySnapshot = null;
        pendingItemTick = -1;
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (pendingItemName == null) return;

        for (ItemStack item : event.getItems()) {
            ItemComposition composition = itemManager.getItemComposition(item.getId());
            if (!composition.getName().equalsIgnoreCase(pendingItemName)) continue;

            collectionLogV2Manager.storeLiveClogItem(item.getId(), 1);
            pendingItemName = null;
            inventorySnapshot = null;
            pendingItemTick = -1;
            return;
        }
    }

    private Map<Integer, Integer> snapshotInventory() {
        Map<Integer, Integer> snapshot = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return snapshot;

        for (Item item : inventory.getItems()) {
            if (item.getId() == -1) continue;
            snapshot.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        return snapshot;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired pre) {
        // Script 4100 fires when collection log items are enumerated via search - this covers
        // both the drop-triggered scan above (confirming a live item's true count) and a plain
        // manual open/browse of the log, which enumerates the player's whole log regardless of
        // whether anything is actually new. storeScannedClogItem tells those two apart by
        // checking confirmedLiveIds, so a manual check-in doesn't get reported as a fresh drop.
        if (pre.getScriptId() == 4100) {
            // Arguments: [widgetId, itemId, qty]
            Object[] args = pre.getScriptEvent().getArguments();
            if (args != null && args.length >= 3) {
                try {
                    int itemId = (int) args[1];
                    int quantity = (int) args[2];
                    collectionLogV2Manager.storeScannedClogItem(itemId, quantity);
                } catch (Exception ignored) {
                    //
                }
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired post) {
        final int COLLECTION_LOG_SETUP = 7797;
        if (post.getScriptId() == COLLECTION_LOG_SETUP) {
            if (searchTriggered) return;
            boolean isAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
            if (isAdventureLog) return;

            searchTriggered = true;
            searchTriggeredTick = client.getTickCount();
            client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
            final int COLLECTION_INIT = 2240;
            client.runScript(COLLECTION_INIT);
        }
    }
}
