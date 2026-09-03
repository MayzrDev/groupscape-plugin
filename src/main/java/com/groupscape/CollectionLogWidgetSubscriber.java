package com.groupscape;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

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
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String message = Text.removeTags(event.getMessage());
        Matcher m = NEW_CLOG_ITEM_PATTERN.matcher(message);
        if (!m.find()) return;

        String itemName = m.group("item").trim();
        int itemId = resolveItemId(itemName);
        if (itemId == -1) {
            log.warn("could not resolve collection log item id for name '{}'", itemName);
            return;
        }

        // Real quantity (for stackable log entries) is only known once the log widget is
        // scanned; this just registers the unlock immediately so it isn't lost until the
        // player next opens the log. onScriptPreFired below overwrites it with the true count.
        collectionLogV2Manager.storeClogItem(itemId, 1);
    }

    // The unlock chat line and ItemManager's search index don't always agree on which apostrophe
    // character a name uses (e.g. "Beekeeper's gloves" vs "Beekeeper's gloves") - a straight
    // equalsIgnoreCase between them then never matches and the drop is silently lost. Normalize
    // every apostrophe-like codepoint to a single character before comparing.
    private static String normalizeApostrophes(String name) {
        return name.replaceAll("[‘’ʼ´`]", "'");
    }

    private int resolveItemId(String itemName) {
        List<ItemPrice> matches = itemManager.search(itemName);
        String normalizedTarget = normalizeApostrophes(itemName);
        for (ItemPrice match : matches) {
            if (normalizeApostrophes(match.getName()).equalsIgnoreCase(normalizedTarget)) {
                return match.getId();
            }
        }
        return -1;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired pre) {
        // Script 4100 fires when collection log items are enumerated via search
        if (pre.getScriptId() == 4100) {
            // Arguments: [widgetId, itemId, qty]
            Object[] args = pre.getScriptEvent().getArguments();
            if (args != null && args.length >= 3) {
                try {
                    int itemId = (int) args[1];
                    int quantity = (int) args[2];
                    collectionLogV2Manager.storeClogItem(itemId, quantity);
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
