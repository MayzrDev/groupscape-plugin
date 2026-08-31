package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.HttpRequestService;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the local player's own active pings: starting one (right-click menu entry or the hold-key
 * shortcut), re-broadcasting a live NPC ping's position each tick, and ending it on timeout,
 * despawn, or manual clear. A player can have at most one active NPC ping and one active tile ping
 * at the same time - starting a new ping only replaces a prior one of the same kind, so e.g.
 * pinging a tile doesn't clear an already-active NPC ping. Every send is a fire-and-forget
 * {@code POST} to {@code /api/characters/{accountHash}/ping}, relayed by the server to every
 * connected overlay (including this client - {@link PingState} is what actually renders it,
 * populated via {@link RosterClient.PingEventListener} regardless of who started the ping).
 *
 * <p>Only ever tracks an NPC ping's NPC by holding the same {@link NPC} object reference handed to
 * {@link #startNpcPing} - RuneLite keeps that instance stable for as long as the NPC hasn't
 * despawned, so no per-tick {@code client.getNpcs()} lookup is needed. This also sidesteps NPC
 * identity not being reliably comparable across different clients/instances: every other client
 * just renders wherever this client's updates say, never resolving the NPC themselves.
 */
@Slf4j
public class PingManager {
    /** Both the tile-ping lifetime and the NPC-ping safety-net ceiling (despawn/death ends it sooner). */
    private static final long MAX_DURATION_MS = 60_000;
    /** How often a live NPC ping re-sends its position - a fraction of a game tick's worth of
     * slack under typical ping, well below the 60s timeout, without spamming an update every tick. */
    private static final long NPC_UPDATE_INTERVAL_MS = 600;

    private final Client client;
    private final HttpRequestService httpRequestService;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groupscape-ping-send");
        t.setDaemon(true);
        return t;
    });

    /** One in-flight ping of a single kind (NPC or tile) started by the local player. */
    private static class OwnPing {
        final String pingId;
        final long startedAtMs;
        /** Only set for an NPC ping - null for a tile ping. */
        NPC trackedNpc;
        long lastUpdateSentAtMs;
        int lastSentX;
        int lastSentY;
        int lastSentPlane;

        OwnPing(String pingId, long startedAtMs) {
            this.pingId = pingId;
            this.startedAtMs = startedAtMs;
        }
    }

    private OwnPing ownNpcPing;
    private OwnPing ownTilePing;

    public PingManager(Client client, HttpRequestService httpRequestService) {
        this.client = client;
        this.httpRequestService = httpRequestService;
    }

    public boolean hasOwnNpcPing() {
        return ownNpcPing != null;
    }

    public boolean hasOwnTilePing() {
        return ownTilePing != null;
    }

    public void startTilePing(WorldPoint worldPoint, GroupScapeTrackerConfig config) {
        if (worldPoint == null) {
            log.debug("Ping: startTilePing called with null worldPoint, ignoring");
            return;
        }
        String memberName = localPlayerName();
        if (memberName == null) {
            log.debug("Ping: startTilePing aborted, no local player name");
            return;
        }

        endTilePing(config);

        OwnPing ping = new OwnPing(UUID.randomUUID().toString(), System.currentTimeMillis());
        ping.lastSentX = worldPoint.getX();
        ping.lastSentY = worldPoint.getY();
        ping.lastSentPlane = worldPoint.getPlane();
        ownTilePing = ping;

        sendStart(ping.pingId, memberName, PingState.KIND_TILE, worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), null, config);
    }

    public void startNpcPing(NPC npc, GroupScapeTrackerConfig config) {
        WorldPoint worldPoint = npc.getWorldLocation();
        if (worldPoint == null) {
            log.debug("Ping: startNpcPing aborted, NPC {} has no world location", npc.getName());
            return;
        }
        String memberName = localPlayerName();
        if (memberName == null) {
            log.debug("Ping: startNpcPing aborted, no local player name");
            return;
        }

        endNpcPing(config);

        OwnPing ping = new OwnPing(UUID.randomUUID().toString(), System.currentTimeMillis());
        ping.trackedNpc = npc;
        ping.lastUpdateSentAtMs = ping.startedAtMs;
        ping.lastSentX = worldPoint.getX();
        ping.lastSentY = worldPoint.getY();
        ping.lastSentPlane = worldPoint.getPlane();
        ownNpcPing = ping;

        sendStart(ping.pingId, memberName, PingState.KIND_NPC, worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), npc.getName(), config);
    }

    /** Manual early clear of the local player's own NPC ping, if any. */
    public void clearOwnNpcPing(GroupScapeTrackerConfig config) {
        endNpcPing(config);
    }

    /** Manual early clear of the local player's own tile ping, if any. */
    public void clearOwnTilePing(GroupScapeTrackerConfig config) {
        endTilePing(config);
    }

    /**
     * Called by {@link com.groupscape.GroupScapeTrackerPlugin#onNpcDespawned} for every despawn -
     * ends the NPC ping if it happens to be the one we're tracking (death or ordinary despawn both
     * end it, per spec; there's no separate "died" signal worth distinguishing here).
     */
    public void onNpcDespawned(NPC npc, GroupScapeTrackerConfig config) {
        if (ownNpcPing != null && ownNpcPing.trackedNpc == npc) {
            endNpcPing(config);
        }
    }

    public void onGameTick(GroupScapeTrackerConfig config) {
        long now = System.currentTimeMillis();

        if (ownTilePing != null && now - ownTilePing.startedAtMs >= MAX_DURATION_MS) {
            endTilePing(config);
        }

        if (ownNpcPing != null) {
            if (now - ownNpcPing.startedAtMs >= MAX_DURATION_MS) {
                endNpcPing(config);
            } else if (ownNpcPing.trackedNpc == null) {
                endNpcPing(config);
            } else {
                WorldPoint worldPoint = ownNpcPing.trackedNpc.getWorldLocation();
                if (worldPoint != null) {
                    boolean moved = worldPoint.getX() != ownNpcPing.lastSentX
                            || worldPoint.getY() != ownNpcPing.lastSentY
                            || worldPoint.getPlane() != ownNpcPing.lastSentPlane;
                    if (moved && now - ownNpcPing.lastUpdateSentAtMs >= NPC_UPDATE_INTERVAL_MS) {
                        ownNpcPing.lastSentX = worldPoint.getX();
                        ownNpcPing.lastSentY = worldPoint.getY();
                        ownNpcPing.lastSentPlane = worldPoint.getPlane();
                        ownNpcPing.lastUpdateSentAtMs = now;
                        sendUpdate(ownNpcPing.pingId, worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), config);
                    }
                }
            }
        }
    }

    public void shutdown() {
        sendExecutor.shutdownNow();
    }

    private void endNpcPing(GroupScapeTrackerConfig config) {
        if (ownNpcPing == null) return;
        sendEnd(ownNpcPing.pingId, config);
        ownNpcPing = null;
    }

    private void endTilePing(GroupScapeTrackerConfig config) {
        if (ownTilePing == null) return;
        sendEnd(ownTilePing.pingId, config);
        ownTilePing = null;
    }

    private String localPlayerName() {
        Player local = client.getLocalPlayer();
        return local == null ? null : local.getName();
    }

    private static class PingRequestBody {
        String action;
        String pingId;
        String memberName;
        String kind;
        Integer x;
        Integer y;
        Integer plane;
        String npcName;
    }

    private void sendStart(String pingId, String memberName, String kind, int x, int y, int plane, String npcName, GroupScapeTrackerConfig config) {
        PingRequestBody body = new PingRequestBody();
        body.action = "start";
        body.pingId = pingId;
        body.memberName = memberName;
        body.kind = kind;
        body.x = x;
        body.y = y;
        body.plane = plane;
        body.npcName = npcName;
        send(body, config);
    }

    private void sendUpdate(String pingId, int x, int y, int plane, GroupScapeTrackerConfig config) {
        PingRequestBody body = new PingRequestBody();
        body.action = "update";
        body.pingId = pingId;
        body.x = x;
        body.y = y;
        body.plane = plane;
        send(body, config);
    }

    private void sendEnd(String pingId, GroupScapeTrackerConfig config) {
        PingRequestBody body = new PingRequestBody();
        body.action = "end";
        body.pingId = pingId;
        send(body, config);
    }

    /** Fire-and-forget - never called from the client thread's blocking path. */
    private void send(PingRequestBody body, GroupScapeTrackerConfig config) {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) {
            log.debug("Ping: not sending {} - apiKey empty={}, accountHash={}", body.action, apiKey.isEmpty(), accountHashValue);
            return;
        }

        String url = httpRequestService.getBaseUrl() + "/api/characters/" + accountHashValue + "/ping";
        sendExecutor.submit(() -> {
            HttpRequestService.HttpResponse response = httpRequestService.post(url, apiKey, body);
            if (!response.isSuccessful()) {
                log.debug("ping {} failed: {} {}", body.action, response.getCode(), response.getBody());
            } else {
                log.debug("ping {} sent ok (pingId={})", body.action, body.pingId);
            }
        });
    }
}
