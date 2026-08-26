package com.groupscape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.groupscape.roster.PartyFrameOverlay;
import com.groupscape.roster.RosterClient;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterNotifier;
import com.groupscape.roster.RosterState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
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
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
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
    private ChatMessageManager chatMessageManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    private NavigationButton navigationButton;
    private RosterState rosterState;
    private RosterClient rosterClient;
    private RosterNotifier rosterNotifier;
    private PartyFrameOverlay partyFrameOverlay;
    private int itemsDeposited = 0;
    private boolean dialogueEventEmitted = false;
    private boolean cachePotions = false;
    private Set<Integer> potionStoreVars;
    private boolean lowHpAlertArmed = true;
    private boolean linkRequiredWarningShown = false;
    private boolean wasInWilderness = false;
    private static final double LOW_HP_ALERT_THRESHOLD = 0.25;
    private static final double LOW_HP_REARM_THRESHOLD = 0.5;
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

        GroupScapePanel panel = new GroupScapePanel(() -> LinkBrowser.browse(httpRequestService.getBaseUrl()));
        navigationButton = NavigationButton.builder()
            .tooltip("GroupScape")
            .icon(createNavigationIcon())
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

        rosterState = new RosterState();
        rosterClient = new RosterClient(okHttpClient, gson, rosterState, this::onGroupKillEvent, groupLinkListener);
        rosterNotifier = new RosterNotifier();
        partyFrameOverlay = new PartyFrameOverlay(client, config, rosterState, dataManager.getNpcDialogueTracker());
        overlayManager.add(partyFrameOverlay);

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
        if (rosterClient != null) {
            rosterClient.shutdown();
            rosterClient = null;
        }
        dataManager.setGroupLinkListener(null);

        log.info("GroupScape Tracker stopped!");
    }

    private static BufferedImage createNavigationIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(39, 174, 96));
        graphics.fillOval(2, 2, 28, 28);
        graphics.setColor(Color.WHITE);
        graphics.setFont(graphics.getFont().deriveFont(20f));
        graphics.drawString("G", 8, 23);
        graphics.dispose();
        return image;
    }

    /**
     * Connects/reconnects the party overlay's WebSocket whenever the configured API key or the
     * client's account hash changes. The server only accepts this connection once the character
     * has been linked to a group via the website ({@code POST /account/characters/link-group}) -
     * an unlinked character gets a 403 (surfaced to the player via
     * {@link GroupLinkListener#onLinkRequired()}) and keeps retrying every 5s until that link
     * exists.
     */
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
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoChangeOften() {
        reconcileRosterConnection();

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
     * Adds a "Fill join prompt" / "Copy name" entry when right-clicking a group member's name in
     * {@link PartyFrameOverlay}, so the player can quick-join a friend's house/boss party without
     * typing their name. "Fill join prompt" writes straight into the native chatbox name-entry
     * var when one is open (see {@link #isAwaitingChatNameInput()}); otherwise it falls back to
     * copying the name to the clipboard so the click is never wasted.
     *
     * TODO(verify against a live client via RuneLite's Widget Inspector before relying on this):
     * {@link #isAwaitingChatNameInput()} and {@link #handleQuickJoin} assume the house/boss-party
     * "enter a friend's name" prompts are ordinary chatbox line input (VarClientInt.INPUT_TYPE /
     * VarClientStr.CHATBOX_TYPED_TEXT), the same mechanism used for public chat. That assumption
     * hasn't been confirmed against the live game and may need adjusting per-dialog.
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (partyFrameOverlay == null) return;

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        RosterMember member = partyFrameOverlay.memberAt(mouse.getX(), mouse.getY());
        if (member == null) return;

        boolean promptOpen = isAwaitingChatNameInput();
        String option = promptOpen ? "Fill join prompt" : "Copy name";

        client.createMenuEntry(-1)
                .setOption(option)
                .setTarget(ColorUtil.wrapWithColorTag(member.name, Color.YELLOW))
                .setType(MenuAction.RUNELITE)
                .onClick(e -> handleQuickJoin(member.name, promptOpen));
    }

    /**
     * 8 is not one of RuneLite's named {@code InputType} constants (PRIVATE_MESSAGE=6, SEARCH=11)
     * but was confirmed live via the Developer Tools Var Inspector: MESLAYERMODE goes 0 -> 8 the
     * instant either the house-join or the boss-party-join "Enter a friend's name" prompt opens.
     */
    private static final int INPUT_TYPE_NAME_ENTRY = 8;

    private boolean isAwaitingChatNameInput() {
        return client.getVarcIntValue(VarClientID.MESLAYERMODE) == INPUT_TYPE_NAME_ENTRY;
    }

    /**
     * Mirrors RuneLite core's own {@code ChatKeyboardListener.applyText}: any non-plain-chat input
     * mode (private message, and by the same logic this name-entry mode) is backed by
     * {@code MESLAYERINPUT}, not {@code CHATINPUT} - writing to the wrong var was why the first
     * attempt at this silently did nothing. The chat line also isn't redrawn just because the var
     * changed; {@code CHAT_TEXT_INPUT_REBUILD} has to be run explicitly afterward, same as core
     * does for its private-message-mode branch.
     */
    private void handleQuickJoin(String name, boolean promptOpen) {
        if (promptOpen) {
            clientThread.invoke(() -> {
                client.setVarcStrValue(VarClientID.MESLAYERINPUT, name);
                client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
            });
            sendChatMessage("Filled join prompt with \"" + name + "\".");
        } else {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null);
            sendChatMessage("Copied \"" + name + "\" to clipboard.");
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

    private void sendChatMessage(String message) {
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(message)
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
