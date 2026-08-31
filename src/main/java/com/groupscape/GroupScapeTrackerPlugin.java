package com.groupscape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.groupscape.roster.GroupSnapshotClient;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.GroupWorldMapPoints;
import com.groupscape.roster.LocalRosterMemberFactory;
import com.groupscape.roster.MemberMapIcons;
import com.groupscape.roster.MinimapLocationOverlay;
import com.groupscape.roster.PartyFrameOverlay;
import com.groupscape.roster.PingArrowIcons;
import com.groupscape.roster.PingManager;
import com.groupscape.roster.PingMinimapOverlay;
import com.groupscape.roster.PingState;
import com.groupscape.roster.PingViewportOverlay;
import com.groupscape.roster.PingWorldMapPoints;
import com.groupscape.roster.RaidMarkerIcons;
import com.groupscape.roster.RaidMarkerManager;
import com.groupscape.roster.RaidMarkerMinimapOverlay;
import com.groupscape.roster.RaidMarkerState;
import com.groupscape.roster.RaidMarkerType;
import com.groupscape.roster.RaidMarkerViewportOverlay;
import com.groupscape.roster.RaidMarkerWorldMapPoints;
import com.groupscape.roster.RosterClient;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterNotifier;
import com.groupscape.roster.RosterState;
import com.groupscape.roster.TileHighlightOverlay;
import com.groupscape.roster.WorldMapCoordinates;
import com.groupscape.sidepanel.LocalGroupSnapshotFactory;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
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
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
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
    private net.runelite.client.game.SpriteManager spriteManager;
    @Inject
    private net.runelite.client.game.SkillIconManager skillIconManager;
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
    private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPointManager;
    @Inject
    private net.runelite.client.ui.overlay.tooltip.TooltipManager tooltipManager;
    @Inject
    private OkHttpClient okHttpClient;
    @Inject
    private Gson gson;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private ConfigManager configManager;
    private NavigationButton navigationButton;
    private RosterState rosterState;
    private RosterClient rosterClient;
    private RosterNotifier rosterNotifier;
    private GroupSnapshotState groupSnapshotState;
    private GroupSnapshotClient groupSnapshotClient;
    private PartyFrameOverlay partyFrameOverlay;
    private TileHighlightOverlay tileHighlightOverlay;
    private MinimapLocationOverlay minimapLocationOverlay;
    private GroupWorldMapPoints groupWorldMapPoints;
    private final MemberMapIcons memberMapIcons = new MemberMapIcons();
    private PingState pingState;
    private PingManager pingManager;
    private PingViewportOverlay pingViewportOverlay;
    private PingMinimapOverlay pingMinimapOverlay;
    private PingWorldMapPoints pingWorldMapPoints;
    private final PingArrowIcons pingArrowIcons = new PingArrowIcons();
    private RaidMarkerState raidMarkerState;
    private RaidMarkerManager raidMarkerManager;
    private RaidMarkerViewportOverlay raidMarkerViewportOverlay;
    private RaidMarkerMinimapOverlay raidMarkerMinimapOverlay;
    private RaidMarkerWorldMapPoints raidMarkerWorldMapPoints;
    private final RaidMarkerIcons raidMarkerIcons = new RaidMarkerIcons();
    /**
     * The sidepanel's own "you" row, rebuilt on the client thread each
     * {@link #updateThingsThatDoChangeOften} tick and read from the Swing EDT via
     * {@link GroupScapePanel}'s refresh timer. RuneLite's {@link Client} is not safe to call from
     * the EDT directly (unlike {@link PartyFrameOverlay}, which reads it during client-thread
     * rendering) - these two fields are the hand-off point.
     */
    private volatile RosterMember localMember;
    private volatile GroupSnapshotMember localSnapshot;
    private int itemsDeposited = 0;
    private boolean dialogueEventEmitted = false;
    private boolean cachePotions = false;
    private Set<Integer> potionStoreVars;
    private boolean lowHpAlertArmed = true;
    private boolean linkRequiredWarningShown = false;
    private boolean wasInWilderness = false;
    /**
     * NPC index -> last-known name, refreshed on every sighting. {@code npc.getName()} can
     * return {@code null} by the time {@link NpcDespawned} fires (composition data already
     * cleared), which previously made {@link #onNpcDespawned} silently drop otherwise-trackable
     * kills with a {@code null} name. Trimmed on despawn so this doesn't grow unbounded.
     */
    private final Map<Integer, String> npcNamesByIndex = new HashMap<>();
    /**
     * NPC index -> last-known non-negative health ratio, refreshed on every hitsplat. The health
     * bar (and so {@code npc.getHealthRatio()}) frequently reverts to -1 before
     * {@link NpcDespawned} actually fires - e.g. a boss's death animation outlasting the health
     * bar's own hide timer - which made the live ratio check at despawn miss most boss kills.
     * Recording the ratio as damage lands lets despawn fall back to "did this NPC ever hit 0"
     * instead of "is it at 0 right now". Trimmed on despawn so this doesn't grow unbounded.
     */
    private final Map<Integer, Integer> npcHealthRatioByIndex = new HashMap<>();
    private static final double LOW_HP_ALERT_THRESHOLD = 0.25;
    private static final double LOW_HP_REARM_THRESHOLD = 0.5;
    private static final int SECONDS_BETWEEN_UPLOADS = 1;
    private static final int SECONDS_BETWEEN_GROUP_SNAPSHOT_POLL = 5;
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
        groupSnapshotState = new GroupSnapshotState();
        groupSnapshotClient = new GroupSnapshotClient(httpRequestService, gson, groupSnapshotState);

        GroupScapePanel panel = new GroupScapePanel(
                () -> LinkBrowser.browse(httpRequestService.getBaseUrl()),
                client, config, rosterState, groupSnapshotState, itemManager, skillIconManager, spriteManager,
                clientThread, () -> localMember, () -> localSnapshot);
        navigationButton = NavigationButton.builder()
            .tooltip("GroupScape")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);

        GroupLinkListener groupLinkListener = new GroupLinkListener() {
            @Override
            public void onLinkRequired() {
                if (linkRequiredWarningShown) return;
                linkRequiredWarningShown = true;
                sendChatMessage("GroupScape: this character isn't linked to a group yet - link it on the website to enable the party overlay.");
            }

            @Override
            public void onLinked() {
                linkRequiredWarningShown = false;
            }
        };
        dataManager.setGroupLinkListener(groupLinkListener);

        pingState = new PingState();
        pingManager = new PingManager(client, httpRequestService);
        raidMarkerState = new RaidMarkerState();
        raidMarkerManager = new RaidMarkerManager(client, httpRequestService);
        rosterClient = new RosterClient(okHttpClient, gson, rosterState, this::onGroupKillEvent, this::onGroupDropEvent,
                new RosterClient.PingEventListener() {
                    @Override
                    public void onPingStart(com.groupscape.roster.RosterWireTypes.PingStartPayload payload) {
                        pingState.start(payload);
                        onGroupPingStart(payload);
                    }

                    @Override
                    public void onPingUpdate(com.groupscape.roster.RosterWireTypes.PingUpdatePayload payload) {
                        pingState.update(payload);
                    }

                    @Override
                    public void onPingEnd(com.groupscape.roster.RosterWireTypes.PingEndPayload payload) {
                        pingState.end(payload);
                    }
                },
                new RosterClient.RaidMarkerEventListener() {
                    @Override
                    public void onMarkerStart(com.groupscape.roster.RosterWireTypes.RaidMarkerStartPayload payload) {
                        raidMarkerState.start(payload);
                    }

                    @Override
                    public void onMarkerUpdate(com.groupscape.roster.RosterWireTypes.RaidMarkerUpdatePayload payload) {
                        raidMarkerState.update(payload);
                    }

                    @Override
                    public void onMarkerEnd(com.groupscape.roster.RosterWireTypes.RaidMarkerEndPayload payload) {
                        raidMarkerState.end(payload);
                    }
                },
                groupLinkListener);
        rosterNotifier = new RosterNotifier();
        partyFrameOverlay = new PartyFrameOverlay(client, config, rosterState, dataManager.getNpcDialogueTracker(), spriteManager);
        overlayManager.add(partyFrameOverlay);
        tileHighlightOverlay = new TileHighlightOverlay(client, config, rosterState);
        overlayManager.add(tileHighlightOverlay);
        minimapLocationOverlay = new MinimapLocationOverlay(client, config, rosterState, tooltipManager, memberMapIcons);
        overlayManager.add(minimapLocationOverlay);
        groupWorldMapPoints = new GroupWorldMapPoints(client, config, rosterState, worldMapPointManager, memberMapIcons);

        pingViewportOverlay = new PingViewportOverlay(client, config, pingState, rosterState);
        overlayManager.add(pingViewportOverlay);
        pingMinimapOverlay = new PingMinimapOverlay(client, config, pingState, rosterState, tooltipManager, pingArrowIcons);
        overlayManager.add(pingMinimapOverlay);
        pingWorldMapPoints = new PingWorldMapPoints(config, pingState, rosterState, worldMapPointManager, pingArrowIcons);

        raidMarkerViewportOverlay = new RaidMarkerViewportOverlay(client, config, raidMarkerState, raidMarkerIcons);
        overlayManager.add(raidMarkerViewportOverlay);
        raidMarkerMinimapOverlay = new RaidMarkerMinimapOverlay(client, config, raidMarkerState, tooltipManager, raidMarkerIcons);
        overlayManager.add(raidMarkerMinimapOverlay);
        raidMarkerWorldMapPoints = new RaidMarkerWorldMapPoints(config, raidMarkerState, worldMapPointManager, raidMarkerIcons);

        log.info("GroupScape Tracker v{} started!", PluginVersion.get());
    }

    @Override
    protected void shutDown() throws Exception {
        collectionLogWidgetSubscriber.shutDown();

        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        cachePotions = false;
        potionStoreVars = null;

        if (partyFrameOverlay != null) {
            overlayManager.remove(partyFrameOverlay);
            partyFrameOverlay = null;
        }
        if (tileHighlightOverlay != null) {
            overlayManager.remove(tileHighlightOverlay);
            tileHighlightOverlay = null;
        }
        if (minimapLocationOverlay != null) {
            overlayManager.remove(minimapLocationOverlay);
            minimapLocationOverlay = null;
        }
        if (groupWorldMapPoints != null) {
            groupWorldMapPoints.clear();
            groupWorldMapPoints = null;
        }
        if (pingViewportOverlay != null) {
            overlayManager.remove(pingViewportOverlay);
            pingViewportOverlay = null;
        }
        if (pingMinimapOverlay != null) {
            overlayManager.remove(pingMinimapOverlay);
            pingMinimapOverlay = null;
        }
        if (pingWorldMapPoints != null) {
            pingWorldMapPoints.clear();
            pingWorldMapPoints = null;
        }
        if (pingManager != null) {
            pingManager.shutdown();
            pingManager = null;
        }
        if (pingState != null) {
            pingState.clear();
            pingState = null;
        }
        if (raidMarkerViewportOverlay != null) {
            overlayManager.remove(raidMarkerViewportOverlay);
            raidMarkerViewportOverlay = null;
        }
        if (raidMarkerMinimapOverlay != null) {
            overlayManager.remove(raidMarkerMinimapOverlay);
            raidMarkerMinimapOverlay = null;
        }
        if (raidMarkerWorldMapPoints != null) {
            raidMarkerWorldMapPoints.clear();
            raidMarkerWorldMapPoints = null;
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.shutdown();
            raidMarkerManager = null;
        }
        if (raidMarkerState != null) {
            raidMarkerState.clear();
            raidMarkerState = null;
        }
        if (rosterClient != null) {
            rosterClient.shutdown();
            rosterClient = null;
        }
        groupSnapshotClient = null;
        if (groupSnapshotState != null) {
            groupSnapshotState.clear();
            groupSnapshotState = null;
        }
        localMember = null;
        localSnapshot = null;
        dataManager.setGroupLinkListener(null);

        log.info("GroupScape Tracker stopped!");
    }

    /**
     * Connects/reconnects the party overlay's WebSocket whenever the configured API key or the
     * client's account hash changes. The server only accepts this connection once the character
     * has been linked to a group via the website ({@code POST /account/characters/link-group}) -
     * an unlinked character gets a 403 (surfaced to the player via
     * {@link GroupLinkListener#onLinkRequired()}) and keeps retrying every 5s until that link
     * exists.
     */
    /**
     * Rebuilds the sidepanel's "you" row on the client thread - see {@link #localMember}'s doc
     * for why this can't just be called lazily from the panel's own Swing refresh timer.
     */
    private void updateLocalSidepanelSnapshot() {
        if (doNotUseThisData()) {
            localMember = null;
            localSnapshot = null;
            return;
        }
        localMember = LocalRosterMemberFactory.build(client, dataManager.getNpcDialogueTracker());
        localSnapshot = LocalGroupSnapshotFactory.build(client);
    }

    private void reconcileRosterConnection() {
        dataManager.identify();

        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();

        if (apiKey.isEmpty() || accountHashValue == -1) {
            rosterClient.disconnect();
            return;
        }

        rosterClient.connect(httpRequestService.getBaseUrl(), String.valueOf(accountHashValue), apiKey);
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
            period = SECONDS_BETWEEN_GROUP_SNAPSHOT_POLL,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void pollGroupSnapshot() {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) return;

        groupSnapshotClient.poll(httpRequestService.getBaseUrl(), String.valueOf(accountHashValue), apiKey);
    }

    @Schedule(
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoChangeOften() {
        reconcileRosterConnection();
        updateLocalSidepanelSnapshot();

        if (doNotUseThisData())
            return;
        Player player = client.getLocalPlayer();
        String playerName = player.getName();
        rosterNotifier.check(rosterState.all(), playerName, config).forEach(this::sendChatMessage);
        dataManager.getResources().update(new ResourcesState(playerName, client));
        dataManager.getSpecialAttack().update(new SpecialAttackState(playerName, client));
        dataManager.getActivePrayers().update(new ActivePrayersState(playerName, client));
        dataManager.getRichPresence().update(new RichPresenceState(playerName, client, dataManager.getNpcDialogueTracker()));

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
        dataManager.getPosition().update(new LocationState(playerName, worldPoint, isOnBoat, client.getWorld()));

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
        checkWildernessEntry();
        if (groupWorldMapPoints != null) {
            groupWorldMapPoints.sync();
        }
        if (pingWorldMapPoints != null) {
            pingWorldMapPoints.sync();
        }
        if (pingManager != null) {
            pingManager.onGameTick(config);
        }
        if (raidMarkerWorldMapPoints != null) {
            raidMarkerWorldMapPoints.sync();
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.onGameTick(config);
        }

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

        if (statChanged.getSkill() == Skill.HITPOINTS) {
            checkLowHpAlert(playerName, statChanged.getBoostedLevel(), statChanged.getLevel());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            portraitCaptureManager.onLogin();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            // Unlike a ping, a raid marker never auto-expires - clear all of the local player's
            // markers on logout/world-hop so they don't linger for the rest of the group while
            // this client is disconnected (the client stays the sole source of truth, same as pings).
            if (raidMarkerManager != null) {
                raidMarkerManager.clearAll(config);
            }
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

    /**
     * Adds a "Copy name" entry when right-clicking a group member's name in
     * {@link PartyFrameOverlay}, copying the name to the clipboard.
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (partyFrameOverlay != null) {
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            RosterMember member = partyFrameOverlay.memberAt(mouse.getX(), mouse.getY());
            if (member != null) {
                client.createMenuEntry(-1)
                        .setOption("Copy name")
                        .setTarget(ColorUtil.wrapWithColorTag(member.name, Color.YELLOW))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> handleCopyName(member.name));
            }
        }

        addPingMenuEntry(event);
        addRaidMarkerMenuEntries(event);
    }

    /**
     * Adds a "Ping" entry to: an NPC's own right-click menu (viewport), the world map's right-click
     * menu (via {@link WorldMapCoordinates}, since the world map has no native right-click menu of
     * its own to piggyback on), or a plain ground tile (viewport) - whichever one applies to this
     * particular menu, checked in that priority order. Mutually exclusive by construction: a given
     * right-click is over exactly one of these at a time. Only shown on shift-right-click, so a
     * plain right-click's menu stays uncluttered.
     */
    private void addPingMenuEntry(MenuOpened event) {
        if (!config.pingsEnabled() || pingManager == null || !isShiftHeld()) return;

        try {
            if (pingManager.hasOwnNpcPing()) {
                client.createMenuEntry(-1)
                        .setOption("Clear NPC ping")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: 'Clear NPC ping' clicked");
                            pingManager.clearOwnNpcPing(config);
                        });
            }
            if (pingManager.hasOwnTilePing()) {
                client.createMenuEntry(-1)
                        .setOption("Clear tile ping")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: 'Clear tile ping' clicked");
                            pingManager.clearOwnTilePing(config);
                        });
            }

            NPC npc = findMenuNpc(event.getMenuEntries());
            if (npc != null) {
                String label = npc.getName() != null ? npc.getName() : "NPC";
                log.debug("Ping: adding 'Ping' menu entry for NPC {}", label);
                client.createMenuEntry(-1)
                        .setOption("Ping")
                        .setTarget(ColorUtil.wrapWithColorTag(label, Color.CYAN))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: NPC 'Ping' entry clicked for {}", label);
                            pingManager.startNpcPing(npc, config);
                        });
                return;
            }

            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            Widget worldMapWidget = client.getWidget(WorldMapCoordinates.WORLD_MAP_VIEW_WIDGET_ID);
            if (worldMapWidget != null && !worldMapWidget.isHidden()
                    && worldMapWidget.getBounds().contains(mouse.getX(), mouse.getY())) {
                WorldPoint worldMapPoint = WorldMapCoordinates.canvasPointToWorldPoint(client, mouse);
                if (worldMapPoint != null) {
                    log.debug("Ping: adding world-map 'Ping' menu entry at {}", worldMapPoint);
                    client.createMenuEntry(-1)
                            .setOption("Ping")
                            .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                            .setType(MenuAction.RUNELITE)
                            .onClick(e -> pingManager.startTilePing(worldMapPoint, config));
                } else {
                    log.debug("Ping: over world-map widget but canvasPointToWorldPoint resolved null");
                }
                return;
            }

            Tile tile = client.getSelectedSceneTile();
            WorldPoint tileWorldPoint = tile != null ? tile.getWorldLocation() : null;
            if (tileWorldPoint != null) {
                log.debug("Ping: adding tile 'Ping' menu entry at {}", tileWorldPoint);
                client.createMenuEntry(-1)
                        .setOption("Ping")
                        .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> pingManager.startTilePing(tileWorldPoint, config));
            } else {
                log.debug("Ping: no NPC/world-map/tile target found for this menu (tile={})", tile);
            }
        } catch (Exception e) {
            log.warn("Failed to add ping menu entry", e);
        }
    }

    /** RuneLite's Client.isKeyPressed uses its own internal key-state array, not
     * java.awt.event.KeyEvent codes - 81 is shift there (same value core plugins like
     * GroundItems/GroundMarkers/MenuEntrySwapper use for their own shift checks). */
    private static final int RUNELITE_VK_SHIFT = 81;

    private boolean isShiftHeld() {
        return client.isKeyPressed(RUNELITE_VK_SHIFT);
    }

    private NPC findMenuNpc(MenuEntry[] entries) {
        if (entries == null) return null;
        for (MenuEntry entry : entries) {
            if (entry.getNpc() != null) {
                return entry.getNpc();
            }
        }
        return null;
    }

    /**
     * Adds a "Raid Markers" submenu (one child entry per {@link RaidMarkerType}) to whichever
     * target applies to this menu - same NPC/world-map-widget/scene-tile priority order as
     * {@link #addPingMenuEntry} - plus a single "Clear my raid markers here" entry if the local
     * player already has any marker active on that exact target. Only shown on shift-right-click,
     * so a plain right-click's menu stays uncluttered.
     */
    private void addRaidMarkerMenuEntries(MenuOpened event) {
        if (!config.raidMarkersEnabled() || raidMarkerManager == null || !isShiftHeld()) return;

        try {
            NPC npc = findMenuNpc(event.getMenuEntries());
            if (npc != null) {
                addRaidMarkerEntriesForNpc(npc);
                return;
            }

            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            Widget worldMapWidget = client.getWidget(WorldMapCoordinates.WORLD_MAP_VIEW_WIDGET_ID);
            if (worldMapWidget != null && !worldMapWidget.isHidden()
                    && worldMapWidget.getBounds().contains(mouse.getX(), mouse.getY())) {
                WorldPoint worldMapPoint = WorldMapCoordinates.canvasPointToWorldPoint(client, mouse);
                if (worldMapPoint != null) {
                    addRaidMarkerEntriesForTile(worldMapPoint);
                }
                return;
            }

            Tile tile = client.getSelectedSceneTile();
            WorldPoint tileWorldPoint = tile != null ? tile.getWorldLocation() : null;
            if (tileWorldPoint != null) {
                addRaidMarkerEntriesForTile(tileWorldPoint);
            }
        } catch (Exception e) {
            log.warn("Failed to add raid marker menu entries", e);
        }
    }

    private void addRaidMarkerEntriesForNpc(NPC npc) {
        if (!raidMarkerManager.ownMarkerTypesOnNpc(npc).isEmpty()) {
            addClearRaidMarkersEntry(() -> raidMarkerManager.clearOwnMarkersOnNpc(npc, config));
        }

        String label = npc.getName() != null ? npc.getName() : "NPC";
        MenuEntry parent = client.createMenuEntry(-1)
                .setOption("Raid Markers")
                .setTarget(ColorUtil.wrapWithColorTag(label, Color.CYAN))
                .setType(MenuAction.RUNELITE);
        Menu submenu = parent.createSubMenu();
        RaidMarkerType[] types = RaidMarkerType.values();
        for (int i = types.length - 1; i >= 0; i--) {
            RaidMarkerType type = types[i];
            if (!isRaidMarkerMenuVisible(type)) continue;
            submenu.createMenuEntry(-1)
                    .setOption(type.displayName)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> raidMarkerManager.dropNpcMarker(type, npc, config));
        }
    }

    private void addRaidMarkerEntriesForTile(WorldPoint worldPoint) {
        if (!raidMarkerManager.ownMarkerTypesOnTile(worldPoint).isEmpty()) {
            addClearRaidMarkersEntry(() -> raidMarkerManager.clearOwnMarkersOnTile(worldPoint, config));
        }

        MenuEntry parent = client.createMenuEntry(-1)
                .setOption("Raid Markers")
                .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                .setType(MenuAction.RUNELITE);
        Menu submenu = parent.createSubMenu();
        RaidMarkerType[] types = RaidMarkerType.values();
        for (int i = types.length - 1; i >= 0; i--) {
            RaidMarkerType type = types[i];
            if (!isRaidMarkerMenuVisible(type)) continue;
            submenu.createMenuEntry(-1)
                    .setOption(type.displayName)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> raidMarkerManager.dropTileMarker(type, worldPoint, config));
        }
    }

    private void addClearRaidMarkersEntry(Runnable onClear) {
        client.createMenuEntry(-1)
                .setOption("Clear my raid markers here")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> onClear.run());
    }

    /**
     * Whether {@code type} should appear in the local player's own "Raid Markers" submenu, per the
     * per-type checkboxes in {@link GroupScapeTrackerConfig}'s "Raid Marker Types" sections. This
     * only ever hides entries from this player's own menu - it has no effect on rendering other
     * group members' markers of that type.
     */
    private boolean isRaidMarkerMenuVisible(RaidMarkerType type) {
        switch (type) {
            case DANGER: return config.showMenuDanger();
            case DEFEND: return config.showMenuDefend();
            case LOOT: return config.showMenuLoot();
            case FOCUS: return config.showMenuFocus();
            case ONE: return config.showMenuOne();
            case TWO: return config.showMenuTwo();
            case THREE: return config.showMenuThree();
            case FOUR: return config.showMenuFour();
            case A: return config.showMenuA();
            case B: return config.showMenuB();
            case C: return config.showMenuC();
            case D: return config.showMenuD();
            default: return true;
        }
    }

    /** Called by {@link RosterClient} when any group member's ping (including the local player's
     * own) starts - posts the chat line the ping spec calls for, regardless of who pinged. */
    private void onGroupPingStart(com.groupscape.roster.RosterWireTypes.PingStartPayload payload) {
        String where = payload.npcName != null ? payload.npcName : "(" + payload.x + ", " + payload.y + ")";
        sendChatMessage(payload.memberName + " pinged " + where);
    }

    private void handleCopyName(String name) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null);
        sendChatMessage("Copied \"" + name + "\" to clipboard.");
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

    /**
     * Called by {@link RosterClient} when another group member's kill arrives over the party
     * overlay WebSocket. The local player's own kills are skipped here since the game already
     * shows them a message.
     */
    private void onGroupKillEvent(String memberName, String npcName) {
        if (!config.notifyBossKill()) return;
        Player local = client.getLocalPlayer();
        if (local != null && memberName.equalsIgnoreCase(local.getName())) return;
        sendChatMessage(memberName + " killed " + npcName + "!");
    }

    private static final Color GROUPSCAPE_CHAT_COLOR = new Color(170, 0, 255);

    private void sendChatMessage(String message) {
        String prefixed = ColorUtil.wrapWithColorTag("[gs] " + message, GROUPSCAPE_CHAT_COLOR);
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(prefixed)
                .build());
    }

    /**
     * Fires once when HP ratio drops below {@link #LOW_HP_ALERT_THRESHOLD}, then re-arms only
     * once it recovers past {@link #LOW_HP_REARM_THRESHOLD} - the gap between the two thresholds
     * is hysteresis so HP oscillating right at the alert line (poison, repeated small hits)
     * doesn't fire a fresh alert on every tick.
     */
    private void checkLowHpAlert(String playerName, int currentHp, int maxHp) {
        if (maxHp <= 0) return;
        double ratio = (double) currentHp / maxHp;

        if (lowHpAlertArmed && ratio < LOW_HP_ALERT_THRESHOLD) {
            lowHpAlertArmed = false;
            Player local = client.getLocalPlayer();
            WorldPoint wp = local == null ? null : local.getWorldLocation();
            int worldX = wp == null ? -1 : wp.getX();
            int worldY = wp == null ? -1 : wp.getY();
            int plane = wp == null ? -1 : wp.getPlane();
            dataManager.getAlertEvents().onLowHp(playerName, currentHp, maxHp, worldX, worldY, plane, client.getWorld());
        } else if (!lowHpAlertArmed && ratio >= LOW_HP_REARM_THRESHOLD) {
            lowHpAlertArmed = true;
        }
    }

    /**
     * {@code Varbits.IN_WILDERNESS} holds the current wilderness level (0 outside it) - the same
     * value the in-game wilderness widget displays - so polling it once per tick here is simpler
     * than filtering {@code VarbitChanged} for the one varbit we care about.
     */
    private void checkWildernessEntry() {
        if (doNotUseThisData()) return;

        int wildernessLevel = client.getVarbitValue(Varbits.IN_WILDERNESS);
        boolean nowInWilderness = wildernessLevel > 0;

        if (nowInWilderness && !wasInWilderness) {
            Player local = client.getLocalPlayer();
            WorldPoint wp = local == null ? null : local.getWorldLocation();
            if (local != null && local.getName() != null && wp != null) {
                dataManager.getAlertEvents().onWildernessEntry(
                        local.getName(), wildernessLevel, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
            }
        }
        wasInWilderness = nowInWilderness;
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
     * No generic "NPC died" event exists in the RuneLite API - {@code getHealthRatio() == 0}
     * (live at despawn, or last recorded via {@link #npcHealthRatioByIndex} if the health bar
     * already hid by then) approximates it for every NPC, not just a curated boss list (that
     * allowlist was dropped: it silently discarded loot from any monster it didn't recognize,
     * which is most of normal play). Known gaps this doesn't special-case: gargoyles/rockslugs
     * (die above 0hp), KQ/Vet'ion (mid-fight transforms), Amoxliatl (non-standard health bar).
     * Dual-NPC encounters (Dusk/Dawn, Eldric the Ice King/Verak Lith) aren't combined either -
     * each half despawning at 0 health fires its own kill event.
     */
    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        cacheNpcName(event.getNpc());
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        cacheNpcName(event.getNpc());
    }

    private void cacheNpcName(NPC npc) {
        String name = npc.getName();
        if (name != null) {
            npcNamesByIndex.put(npc.getIndex(), name);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();
        int ratio = npc.getHealthRatio();
        if (ratio >= 0) {
            npcHealthRatioByIndex.put(npc.getIndex(), ratio);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (pingManager != null) {
            pingManager.onNpcDespawned(event.getNpc(), config);
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.onNpcDespawned(event.getNpc(), config);
        }

        if (doNotUseThisData()) return;

        NPC npc = event.getNpc();
        String name = npc.getName();
        if (name == null) {
            name = npcNamesByIndex.get(npc.getIndex());
        }
        npcNamesByIndex.remove(npc.getIndex());
        Integer lastKnownRatio = npcHealthRatioByIndex.remove(npc.getIndex());
        boolean diedAtZeroHp = npc.getHealthRatio() == 0 || (lastKnownRatio != null && lastKnownRatio == 0);
        if (name == null || !diedAtZeroHp) return;

        WorldPoint wp = npc.getWorldLocation();
        if (wp == null) return;

        String playerName = client.getLocalPlayer().getName();
        dataManager.getKillLootDeathEvents().onKill(playerName, npc.getId(), name, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    /**
     * {@code LootReceived} is RuneLite's own client-side wrapper around the authoritative
     * in-game loot-tracker script signal, preferred over a same-tick/same-tile ItemSpawned
     * correlation. NPC loot is also correlated to a pending kill best-effort by
     * {@link KillLootDeathEvents#onLoot}. Chest/instance rewards (raids, Barrows, Gauntlet, etc.
     * - {@link ChestLootSourceNames}) and clue scroll caskets ({@link ClueTier}) arrive as
     * {@code LootRecordType.EVENT}; unlike NPC loot there's no kill to correlate to, so they're
     * logged as their own standalone "loot" event via
     * {@link KillLootDeathEvents#onChestOrClueLoot}. PvP/pickpocket loot sources aren't logged at
     * all yet, but everything is still eligible for the notable-drop check below regardless.
     */
    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (event.getType() == LootRecordType.NPC) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack item : event.getItems()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("itemId", item.getId());
                entry.put("quantity", item.getQuantity());
                items.add(entry);
            }
            dataManager.getKillLootDeathEvents().onLoot(event.getName(), items);
        } else if (event.getType() == LootRecordType.EVENT) {
            String clueTier = ClueTier.extractTier(event.getName());
            boolean isClue = clueTier != null;
            boolean isChest = !isClue && ChestLootSourceNames.isTrackedChest(event.getName());
            if (isClue || isChest) {
                Player local = client.getLocalPlayer();
                WorldPoint wp = local == null ? null : local.getWorldLocation();
                if (wp != null) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (ItemStack item : event.getItems()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("itemId", item.getId());
                        entry.put("quantity", item.getQuantity());
                        items.add(entry);
                    }
                    dataManager.getKillLootDeathEvents().onChestOrClueLoot(
                            local.getName(), isClue ? "clue" : "chest", event.getName(), clueTier,
                            wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(), items);
                }
            }
        }

        checkNotableDrop(event.getType(), event.getName(), event.getItems());
    }

    /**
     * Flags any loot batch whose total GE value crosses
     * {@link GroupScapeTrackerConfig#notableDropThreshold()} for a group chat broadcast,
     * regardless of {@link LootRecordType} - unlike {@link KillLootDeathEvents#onLoot}, this
     * isn't limited to NPC kills. Sending is unconditional once the threshold is crossed
     * (matches {@link #onGroupKillEvent}'s "your own kills always get broadcast" behavior);
     * {@link GroupScapeTrackerConfig#notifyNotableDrop()} only gates whether incoming broadcasts
     * get displayed, in {@link #onGroupDropEvent}.
     */
    private void checkNotableDrop(LootRecordType type, String sourceName, Collection<ItemStack> items) {
        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) return;

        long totalValue = 0;
        ItemStack highlight = null;
        long highlightValue = -1;
        for (ItemStack item : items) {
            long value = (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
            totalValue += value;
            if (value > highlightValue) {
                highlightValue = value;
                highlight = item;
            }
        }
        if (highlight == null || totalValue < config.notableDropThreshold()) return;

        String itemName = itemManager.getItemComposition(highlight.getId()).getName();
        dataManager.getNotableDropEvents().onNotableDrop(
                local.getName(), notableDropSourceType(type), sourceName, itemName, highlightValue, totalValue);
    }

    private static String notableDropSourceType(LootRecordType type) {
        switch (type) {
            case NPC:
                return "kill";
            case PLAYER:
                return "pvp";
            case EVENT:
                return "chest";
            case PICKPOCKET:
                return "pickpocket";
            default:
                return "unknown";
        }
    }

    /**
     * Called by {@link RosterClient} when a group member's notable drop arrives over the party
     * overlay WebSocket - unlike {@link #onGroupKillEvent}, this also fires for the dropper's
     * own drop (nothing else tells them their own drop was notable), so there's no "skip if it's
     * me" guard here. {@code message} is already fully formatted server-side.
     */
    private void onGroupDropEvent(String memberName, String message) {
        if (!config.notifyNotableDrop()) return;
        sendChatMessage(message);
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
        if (player == null) return;

        Actor actor = player.getInteracting();
        if (actor != null) {
            String playerName = player.getName();
            dataManager.getInteracting().update(new InteractingState(playerName, actor, client));

            if (actor instanceof NPC) {
                NPC npc = (NPC) actor;
                dataManager.getNpcDialogueTracker().observe(npc.getId(), npc.getName(), npc.getCombatLevel());
            }
        }

        updateNpcDialogueEvent(player);
    }

    /**
     * Emits one "dialogue" interaction event per dialogue session (start), not once per tick
     * while the box stays open - {@code dialogueEventEmitted} dedupes across ticks and is reset
     * as soon as the dialogue widgets close.
     */
    private void updateNpcDialogueEvent(Player player) {
        NpcDialogueTracker tracker = dataManager.getNpcDialogueTracker();

        if (!isDialogueOpen()) {
            tracker.clear();
            dialogueEventEmitted = false;
            return;
        }

        Integer npcId = tracker.lastNpcId();
        if (npcId == null || dialogueEventEmitted) return;

        WorldPoint wp = player.getWorldLocation();
        if (wp == null) return;

        dataManager.getInteractionEvents().onDialogue(player.getName(), npcId, tracker.lastNpcName(), tracker.lastCombatLevel(),
                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
        dialogueEventEmitted = true;
    }

    /**
     * The chat-dialogue box (NPC text or the option-select menu) being open is the one signal
     * that actually distinguishes "talking to" from "fighting" - both fire the same
     * {@code getInteracting()} target. Ported from groupscape-old's equivalent check.
     */
    private boolean isDialogueOpen() {
        Widget npcText = client.getWidget(InterfaceID.ChatLeft.TEXT);
        if (npcText != null && !npcText.isHidden()) {
            return true;
        }
        Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        return options != null && !options.isHidden();
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
