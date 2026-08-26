package com.groupscape;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class DataManager {
    @Inject
    Client client;
    @Inject
    GroupScapeTrackerConfig config;
    @Inject
    private CollectionLogV2Manager collectionLogV2Manager;
    @Inject
    private HttpRequestService httpRequestService;
    @Inject
    private ScheduledExecutorService executor;
    private int skipNextNAttempts = 0;
    private GroupLinkListener groupLinkListener;

    /** Called once from plugin startup; not constructor-injected since Guice owns this singleton. */
    public void setGroupLinkListener(GroupLinkListener groupLinkListener) {
        this.groupLinkListener = groupLinkListener;
    }

    @Getter
    private final DataState inventory = new DataState("inventory", false);
    @Getter
    private final DataState bank = new DataState("bank", false);
    @Getter
    private final DataState equipment = new DataState("equipment", false);
    @Getter
    private final DataState sharedBank = new DataState("shared_bank", true);
    @Getter
    private final DataState resources = new DataState("stats", false);
    @Getter
    private final DataState skills = new DataState("skills", false);
    @Getter
    private final DataState quests = new DataState("quests", false);
    @Getter
    private final DataState position = new DataState("coordinates", false);
    @Getter
    private final DataState runePouch = new DataState("rune_pouch", false);
    @Getter
    private final DataState quiver = new DataState("quiver", false);
    @Getter
    private final DataState interacting = new DataState("interacting", false);
    @Getter
    private final DataState seedVault = new DataState("seed_vault", false);
    @Getter
    private final DataState potionStorage = new DataState("potion_storage", false);
    @Getter
    private final DataState achievementDiary = new DataState("diary_vars", false);
    @Getter
    private final DataState combatAchievements = new DataState("combat_achievements", false);
    @Getter
    private final DataState specialAttack = new DataState("special_attack", false);
    @Getter
    private final DataState activePrayers = new DataState("active_prayers", false);
    @Getter
    private final DataState richPresence = new DataState("rich_presence", false);
    @Getter
    private final DepositedItems deposited = new DepositedItems();
    @Getter
    private final KillLootDeathEvents killLootDeathEvents = new KillLootDeathEvents();
    @Getter
    private final ObjectInteractionEvents objectInteractionEvents = new ObjectInteractionEvents();
    @Getter
    private final InteractionEvents interactionEvents = new InteractionEvents();
    @Getter
    private final NpcDialogueTracker npcDialogueTracker = new NpcDialogueTracker();
    @Getter
    private final AlertEvents alertEvents = new AlertEvents();

    public void submitToApi() {
        if (config.pauseSync()) return;
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null || isBadWorldType()) return;
        if (skipNextNAttempts-- > 0) return;

        String playerName = client.getLocalPlayer().getName();
        String apiKey = config.apiKey().trim();

        if (apiKey.length() > 0) {
            String url = getUpdateGroupMemberUrl();
            if (url == null) return;

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", playerName);
            inventory.consumeState(updates);
            bank.consumeState(updates);
            equipment.consumeState(updates);
            sharedBank.consumeState(updates);
            resources.consumeState(updates);
            skills.consumeState(updates);
            quests.consumeState(updates);
            position.consumeState(updates);
            runePouch.consumeState(updates);
            quiver.consumeState(updates);
            interacting.consumeState(updates);
            deposited.consumeState(updates);
            seedVault.consumeState(updates);
            potionStorage.consumeState(updates);
            achievementDiary.consumeState(updates);
            combatAchievements.consumeState(updates);
            specialAttack.consumeState(updates);
            activePrayers.consumeState(updates);
            richPresence.consumeState(updates);
            collectionLogV2Manager.consumeClogItems(updates);
            killLootDeathEvents.consumeState(updates);
            objectInteractionEvents.consumeState(updates);
            interactionEvents.consumeState(updates);
            alertEvents.consumeState(updates);

            if (updates.size() > 1) {
                HttpRequestService.HttpResponse response = httpRequestService.post(url, apiKey, updates);

                if (!response.isSuccessful()) {
                    skipNextNAttempts = 10;
                    restoreStateIfNothingUpdated();
                    if (response.getCode() == 403 && groupLinkListener != null) {
                        groupLinkListener.onLinkRequired();
                    }
                } else {
                    collectionLogV2Manager.clearClogItems();
                    if (groupLinkListener != null) {
                        groupLinkListener.onLinked();
                    }
                }
            } else {
                log.debug("Skip POST: no changes to send (fields={})", updates.size());
            }
        }
    }

    public void uploadPortrait(byte[] mesh) {
        if (config.pauseSync()) return;
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null || isBadWorldType()) return;

        String playerName = client.getLocalPlayer().getName();
        String apiKey = config.apiKey().trim();
        // The /update-portrait route is behind the same Authenticated middleware as everything
        // else, so a bad key or an unlinked character still fails safely below.
        if (apiKey.isEmpty()) return;

        String url = getUpdatePortraitUrl(playerName);
        if (url == null) return;

        // capture() runs on the client thread (needed to read the live Model), but OkHttp's
        // blocking call() is not allowed there - RuneLite throws "Blocking network calls are
        // not allowed on the client thread" and the upload silently never lands. Everything
        // above this point only touches client-thread state, so hand off just the network call.
        executor.execute(() -> {
            HttpRequestService.HttpResponse response =
                    httpRequestService.postBytes(url, apiKey, mesh, "application/octet-stream");

            if (!response.isSuccessful()) {
                log.debug("Portrait upload failed ({} bytes): {} {}", mesh.length, response.getCode(), response.getBody());
            }
        });
    }

    /**
     * Lets the server learn this character's RSN even before it has been linked to a group -
     * needed for a website confirmation UI. Cheap and idempotent, so it's fine to call on the
     * same ~1s cadence as the rest of the periodic reconciliation. Called from the client thread
     * (see reconcileRosterConnection()), so - like uploadPortrait() - the actual network call is
     * handed off to the executor rather than blocking here.
     */
    public void identify() {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return;

        String apiKey = config.apiKey().trim();
        if (apiKey.isEmpty()) return;

        String url = getIdentifyUrl();
        if (url == null) return;

        String playerName = client.getLocalPlayer().getName();
        Map<String, Object> body = new HashMap<>();
        body.put("rsn", playerName);

        executor.execute(() -> httpRequestService.post(url, apiKey, body));
    }

    // NOTE: These states should only be restored if a new update did not come in at some point before calling this
    private void restoreStateIfNothingUpdated() {
        inventory.restoreState();
        bank.restoreState();
        equipment.restoreState();
        sharedBank.restoreState();
        resources.restoreState();
        skills.restoreState();
        quests.restoreState();
        position.restoreState();
        runePouch.restoreState();
        quiver.restoreState();
        interacting.restoreState();
        deposited.restoreState();
        seedVault.restoreState();
        potionStorage.restoreState();
        achievementDiary.restoreState();
        combatAchievements.restoreState();
        specialAttack.restoreState();
        activePrayers.restoreState();
        richPresence.restoreState();
        killLootDeathEvents.restoreState();
        objectInteractionEvents.restoreState();
        interactionEvents.restoreState();
        alertEvents.restoreState();
    }

    private String baseUrl() {
        return httpRequestService.getBaseUrl();
    }

    /** Returns null (matching the old "missing config" guard) when the account hash isn't ready yet. */
    private String accountHash() {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) return null;

        return String.valueOf(accountHash);
    }

    private String getUpdateGroupMemberUrl() {
        String baseUrl = baseUrl();
        String accountHash = accountHash();

        if (baseUrl == null || accountHash == null) return null;

        return String.format("%s/api/characters/%s/update-group-member", baseUrl, accountHash);
    }

    private String getUpdatePortraitUrl(String playerName) {
        String baseUrl = baseUrl();
        String accountHash = accountHash();

        if (baseUrl == null || accountHash == null) return null;

        return String.format("%s/api/characters/%s/update-portrait/%s", baseUrl, accountHash, urlEncodePathSegment(playerName));
    }

    private String getIdentifyUrl() {
        String baseUrl = baseUrl();
        String accountHash = accountHash();

        if (baseUrl == null || accountHash == null) return null;

        return String.format("%s/api/characters/%s/identify", baseUrl, accountHash);
    }

    // RuneScape names can contain spaces (e.g. "Zezima 1"), which OkHttp's Request.Builder.url()
    // rejects unencoded, silently failing the request before it's ever sent.
    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    // URLEncoder is query-string encoding (space -> '+'); a path segment needs percent-encoding
    // (space -> "%20") since actix percent-decodes path segments but won't treat '+' as a space.
    private static String urlEncodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }

    private boolean isBadWorldType() {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        for (WorldType worldType : worldTypes) {
            if (worldType == WorldType.SEASONAL ||
                    worldType == WorldType.DEADMAN ||
                    worldType == WorldType.TOURNAMENT_WORLD ||
                    worldType == WorldType.PVP_ARENA ||
                    worldType == WorldType.BETA_WORLD ||
                    worldType == WorldType.QUEST_SPEEDRUNNING) {
                return true;
            }
        }

        return false;
    }
}
