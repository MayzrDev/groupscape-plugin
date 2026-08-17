package com.groupscape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.groupscape.roster.PartyFrameOverlay;
import com.groupscape.roster.RosterClient;
import com.groupscape.roster.RosterState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.WorldView;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "GroupScape"
)
public class GroupScapeTrackerPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private DataManager dataManager;
    @Inject
    private ItemManager itemManager;
    @Inject
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;
    @Inject
    private PortraitCaptureManager portraitCaptureManager;
    @Inject
    private GroupScapeTrackerConfig config;
    @Inject
    private HttpRequestService httpRequestService;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private OkHttpClient okHttpClient;
    @Inject
    private Gson gson;
    @Inject
    private ClientToolbar clientToolbar;
    private RosterState rosterState;
    private RosterClient rosterClient;
    private PartyFrameOverlay partyFrameOverlay;
    private GroupScapePanel panel;
    private NavigationButton navigationButton;
    private int itemsDeposited = 0;
    private boolean cachePotions = false;
    private Set<Integer> potionStoreVars;
    private static final int SECONDS_BETWEEN_UPLOADS = 1;
    private static final int SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES = 60;
    private static final int SECONDS_BETWEEN_PORTRAIT_BACKSTOP = 300;
    private static final int DEPOSIT_ITEM = 12582914;
    private static final int DEPOSIT_INVENTORY = 12582916;
    private static final int DEPOSIT_EQUIPMENT = 12582918;
    private static final int CHATBOX_ENTERED = 681;
    private static final int GROUP_STORAGE_LOADER = 293;

    @Override
    protected void startUp() throws Exception {
        collectionLogWidgetSubscriber.startUp();

        rosterState = new RosterState();
        rosterClient = new RosterClient(okHttpClient, gson, rosterState);
        partyFrameOverlay = new PartyFrameOverlay(client, config, rosterState);
        overlayManager.add(partyFrameOverlay);

        panel = new GroupScapePanel();
        panel.setLinkCharacterListener(this::onLinkCharacterClicked);
        navigationButton = NavigationButton.builder()
                .tooltip("GroupScape")
                .icon(buildIcon())
                .priority(5)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navigationButton);

        log.info("GroupScape Tracker v{} started!", PluginVersion.get());
    }

    @Override
    protected void shutDown() throws Exception {
        collectionLogWidgetSubscriber.shutDown();
        cachePotions = false;
        potionStoreVars = null;

        if (partyFrameOverlay != null) {
            overlayManager.remove(partyFrameOverlay);
            partyFrameOverlay = null;
        }
        if (rosterClient != null) {
            rosterClient.shutdown();
            rosterClient = null;
        }
        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
            panel = null;
        }

        log.info("GroupScape Tracker stopped!");
    }

    /**
     * Opens the website's {@code /link} page carrying this character's account hash + RSN — the
     * site's already-authenticated session does the actual linking, so the plugin has nothing to
     * POST and no pass/fail result to show; confirmation happens entirely in the browser tab
     * this opens.
     */
    private void onLinkCharacterClicked() {
        long accountHash = client.getAccountHash();
        Player local = client.getLocalPlayer();
        if (accountHash == -1 || local == null || local.getName() == null) {
            return;
        }

        String url = httpRequestService.getBaseUrl() + "/link?accountHash="
                + urlEncode(String.valueOf(accountHash)) + "&rsn=" + urlEncode(local.getName());
        try {
            LinkBrowser.browse(url);
        } catch (RuntimeException e) {
            log.warn("GroupScape failed to open the link page: {}", e.getMessage());
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static BufferedImage buildIcon() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(0x1F7A4D));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.fillOval(5, 5, 6, 6);
        g.dispose();
        return icon;
    }

    /** Connects/reconnects the party overlay's WebSocket whenever the configured group or token changes. */
    private void reconcileRosterConnection() {
        String token = config.authorizationToken().trim();
        String groupName = GroupToken.parseGroupName(token);

        if (groupName == null || groupName.isEmpty() || token.isEmpty()) {
            rosterClient.disconnect();
            return;
        }

        rosterClient.connect(httpRequestService.getBaseUrl(), groupName, token);
    }

    @Schedule(
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void submitToApi() {
        dataManager.submitToApi();
    }

    @Schedule(
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoChangeOften() {
        reconcileRosterConnection();

        if (doNotUseThisData())
            return;
        Player player = client.getLocalPlayer();
        String playerName = player.getName();
        dataManager.getResources().update(new ResourcesState(playerName, client));
        dataManager.getSpecialAttack().update(new SpecialAttackState(playerName, client));
        dataManager.getActivePrayers().update(new ActivePrayersState(playerName, client));
        dataManager.getRichPresence().update(new RichPresenceState(playerName, client));

        LocalPoint localPoint = player.getLocalLocation();
        WorldView worldView = player.getWorldView();
        int worldViewId = worldView.getId();
        boolean isOnBoat = worldViewId != WorldView.TOPLEVEL;
        WorldPoint worldPoint;
        if (isOnBoat) {
            WorldEntity worldEntity = client.getTopLevelWorldView().worldEntities().byIndex(worldViewId);
            worldPoint = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
        } else {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        }
        dataManager.getPosition().update(new LocationState(playerName, worldPoint, isOnBoat));

        dataManager.getRunePouch().update(new RunePouchState(playerName, client));
        dataManager.getQuiver().update(new QuiverState(playerName, client, itemManager));
    }

    @Schedule(
            period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoNotChangeOften() {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        dataManager.getQuests().update(new QuestState(playerName, client));
        dataManager.getAchievementDiary().update(new AchievementDiaryState(playerName, client));
        dataManager.getCombatAchievements().update(new CombatAchievementState(playerName, client));
    }

    @Schedule(
            period = SECONDS_BETWEEN_PORTRAIT_BACKSTOP,
            unit = ChronoUnit.SECONDS
    )
    public void capturePortraitBackstop() {
        portraitCaptureManager.captureIfLoggedIn();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (doNotUseThisData()) return;

        final int varpId = event.getVarpId();
        if (varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) {
            String playerName = client.getLocalPlayer().getName();
            dataManager.getQuiver().update(new QuiverState(playerName, client, itemManager));
        }

        if (potionStoreVars != null && potionStoreVars.contains(varpId)) {
            cachePotions = true;
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (cachePotions) {
            cachePotions = false;
            updatePotionStorage();

            Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
            if (w != null && potionStoreVars == null) {
                int[] trigger = w.getVarTransmitTrigger();
                potionStoreVars = new HashSet<>();
                for (int varId : trigger) {
                    potionStoreVars.add(varId);
                }
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        --itemsDeposited;
        updateInteracting();

        Widget groupStorageLoaderText = client.getWidget(GROUP_STORAGE_LOADER, 1);
        if (groupStorageLoaderText != null) {
            if (groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
                dataManager.getSharedBank().commitTransaction();
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        dataManager.getSkills().update(new SkillState(playerName, client));
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            portraitCaptureManager.onLogin();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        final int id = event.getContainerId();
        ItemContainer container = event.getItemContainer();

        if (id == InventoryID.BANK) {
            dataManager.getDeposited().reset();
            dataManager.getBank().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.SEED_VAULT) {
            dataManager.getSeedVault().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.INV) {
            ItemContainerState newInventoryState = new ItemContainerState(playerName, container, itemManager, 28);
            if (itemsDeposited > 0) {
                updateDeposited(newInventoryState, (ItemContainerState) dataManager.getInventory().mostRecentState());
            }

            dataManager.getInventory().update(newInventoryState);
        } else if (id == InventoryID.WORN) {
            ItemContainerState newEquipmentState = new ItemContainerState(playerName, container, itemManager, 14);
            if (itemsDeposited > 0) {
                updateDeposited(newEquipmentState, (ItemContainerState) dataManager.getEquipment().mostRecentState());
            }

            dataManager.getEquipment().update(newEquipmentState);
            portraitCaptureManager.onEquipmentSynced();
        } else if (id == InventoryID.INV_GROUP_TEMP) {
            dataManager.getSharedBank().update(new ItemContainerState(playerName, container, itemManager));
        }
    }

    @Subscribe
    private void onScriptPreFired(ScriptPreFired event) {
        if (doNotUseThisData()) return;
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            cachePotions = true;
        }
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == CHATBOX_ENTERED && client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null) {
            itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        final int param1 = event.getParam1();
        final MenuAction menuAction = event.getMenuAction();
        if (menuAction == MenuAction.CC_OP) {
            if (param1 == DEPOSIT_ITEM || param1 == DEPOSIT_INVENTORY || param1 == DEPOSIT_EQUIPMENT) {
                itemsMayHaveBeenDeposited();
            }
        } else if (isGameObjectAction(menuAction)) {
            recordObjectInteraction(event);
        }
    }

    private static boolean isGameObjectAction(MenuAction menuAction) {
        return menuAction == MenuAction.GAME_OBJECT_FIRST_OPTION
                || menuAction == MenuAction.GAME_OBJECT_SECOND_OPTION
                || menuAction == MenuAction.GAME_OBJECT_THIRD_OPTION
                || menuAction == MenuAction.GAME_OBJECT_FOURTH_OPTION
                || menuAction == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    /**
     * {@code Actor.getInteracting()} (used for NPC/combat presence, see {@link #updateInteracting})
     * never fires for clicking a game object - a Wintertodt brazier, a Guardians of the Rift
     * portal, a fishing spot - so this is the only signal available for object interactions.
     * Fires on the click itself rather than any confirmation the action completed, matching
     * this class's other best-effort event captures.
     */
    private void recordObjectInteraction(MenuOptionClicked event) {
        if (doNotUseThisData()) return;

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) return;

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        String objectName = Text.removeTags(event.getMenuTarget());
        dataManager.getObjectInteractionEvents().onObjectInteraction(
                local.getName(), event.getId(), objectName, event.getMenuOption(),
                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() != client.getLocalPlayer()) return;
        updateInteracting();
    }

    private void itemsMayHaveBeenDeposited() {
        // NOTE: In order to determine if an item has gone through the deposit box we first detect if any of the menu
        // actions were performed OR a custom amount was entered while the deposit box inventory widget was opened.
        // Then we allow up to two game ticks were an inventory changed event can occur and at that point we assume
        // it must have been caused by the action detected just before. We can't check the inventory at the time of
        // either interaction since the inventory may have not been updated yet. We also cannot just check that the deposit
        // box window is open in the item container event since it is possible for a player to close the widget before
        // the event handler is called.
        itemsDeposited = 2;
    }

    /**
     * No generic "NPC died" event exists in the RuneLite API (see {@link BossKillNpcNames}'s
     * class doc) - {@code getHealthRatio() == 0} plus the curated name allowlist approximates
     * it, ported from groupscape-old.
     */
    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (doNotUseThisData()) return;

        NPC npc = event.getNpc();
        String name = npc.getName();
        if (!BossKillNpcNames.isTrackedBoss(name) || npc.getHealthRatio() != 0) return;

        WorldPoint wp = npc.getWorldLocation();
        if (wp == null) return;

        String playerName = client.getLocalPlayer().getName();
        dataManager.getKillLootDeathEvents().onKill(playerName, npc.getId(), name, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    /**
     * {@code LootReceived} is RuneLite's own client-side wrapper around the authoritative
     * in-game loot-tracker script signal, preferred over a same-tick/same-tile ItemSpawned
     * correlation. Correlated to a pending kill best-effort by
     * {@link KillLootDeathEvents#onLoot}; chest/pickpocket/clue-scroll loot sources bypass
     * NPC-loot events entirely and are out of scope here.
     */
    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (event.getType() != LootRecordType.NPC) return;

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack item : event.getItems()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("itemId", item.getId());
            entry.put("quantity", item.getQuantity());
            items.add(entry);
        }

        dataManager.getKillLootDeathEvents().onLoot(event.getName(), items);
    }

    /**
     * {@code ActorDeath} filtered to the local player. Killer attribution is best-effort only
     * via {@code Actor.getInteracting()} (the closest available signal - there is no attacker
     * field on hitsplats), ported from groupscape-old.
     */
    @Subscribe
    public void onActorDeath(ActorDeath event) {
        Player local = client.getLocalPlayer();
        if (event.getActor() != local) return;

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        Actor interacting = local.getInteracting();
        String killerName = interacting != null ? interacting.getName() : null;
        dataManager.getKillLootDeathEvents().onDeath(local.getName(), wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(), killerName);
    }

    private void updateInteracting() {
        Player player = client.getLocalPlayer();

        if (player != null) {
            Actor actor = player.getInteracting();

            if (actor != null) {
                String playerName = player.getName();
                dataManager.getInteracting().update(new InteractingState(playerName, actor, client));
            }
        }
    }

    private void updateDeposited(ItemContainerState newState, ItemContainerState previousState) {
        ItemContainerState deposited = newState.whatGotRemoved(previousState);
        dataManager.getDeposited().update(deposited);
    }

    private void updatePotionStorage() {
        Player player = client.getLocalPlayer();

        if (player != null) {
            PotionStorageState potionStorageState = PotionStorageState.fromClient(player.getName(), client, itemManager);
            dataManager.getPotionStorage().update(potionStorageState);
        }
    }

    private boolean doNotUseThisData() {
        return client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null;
    }

    @Provides
    GroupScapeTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GroupScapeTrackerConfig.class);
    }
}
