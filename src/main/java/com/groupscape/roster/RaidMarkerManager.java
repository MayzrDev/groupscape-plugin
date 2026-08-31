package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.HttpRequestService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the local player's own active raid markers: up to one marker of each {@link RaidMarkerType}
 * on a tile and one of each type on an NPC (8 independent slots total), started from the
 * "Raid Markers" right-click submenu. Unlike {@link PingManager}, a marker never auto-expires - it
 * is cleared only by a manual clear/redrop, the tracked NPC despawning, or the plugin shutting
 * down/logging out ({@link com.groupscape.GroupScapeTrackerPlugin#onGameStateChanged}). Every send
 * is a fire-and-forget {@code POST} to {@code /api/characters/{accountHash}/raid-marker}, relayed
 * by the server to every connected overlay (including this client - {@link RaidMarkerState} is
 * what actually renders it).
 */
@Slf4j
public class RaidMarkerManager {
    /** How often a live NPC marker re-sends its position - same cadence as {@link PingManager}. */
    private static final long NPC_UPDATE_INTERVAL_MS = 600;

    private final Client client;
    private final HttpRequestService httpRequestService;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groupscape-raid-marker-send");
        t.setDaemon(true);
        return t;
    });

    /** One in-flight marker of a single type+kind started by the local player. */
    private static class OwnMarker {
        final String markerId;
        /** Only set for an NPC marker - null for a tile marker. */
        NPC trackedNpc;
        long lastUpdateSentAtMs;
        int lastSentX;
        int lastSentY;
        int lastSentPlane;

        OwnMarker(String markerId) {
            this.markerId = markerId;
        }
    }

    private final Map<RaidMarkerType, OwnMarker> ownTileMarkers = new EnumMap<>(RaidMarkerType.class);
    private final Map<RaidMarkerType, OwnMarker> ownNpcMarkers = new EnumMap<>(RaidMarkerType.class);

    public RaidMarkerManager(Client client, HttpRequestService httpRequestService) {
        this.client = client;
        this.httpRequestService = httpRequestService;
    }

    public boolean hasOwnTileMarker(RaidMarkerType type) {
        return ownTileMarkers.containsKey(type);
    }

    public boolean hasOwnNpcMarker(RaidMarkerType type) {
        return ownNpcMarkers.containsKey(type);
    }

    /** Every marker type the local player currently has active on this exact NPC. */
    public Set<RaidMarkerType> ownMarkerTypesOnNpc(NPC npc) {
        Set<RaidMarkerType> types = EnumSet.noneOf(RaidMarkerType.class);
        for (Map.Entry<RaidMarkerType, OwnMarker> entry : ownNpcMarkers.entrySet()) {
            if (entry.getValue().trackedNpc == npc) {
                types.add(entry.getKey());
            }
        }
        return types;
    }

    /** Every marker type the local player currently has active on this exact tile. */
    public Set<RaidMarkerType> ownMarkerTypesOnTile(WorldPoint worldPoint) {
        Set<RaidMarkerType> types = EnumSet.noneOf(RaidMarkerType.class);
        for (Map.Entry<RaidMarkerType, OwnMarker> entry : ownTileMarkers.entrySet()) {
            OwnMarker marker = entry.getValue();
            if (marker.lastSentX == worldPoint.getX() && marker.lastSentY == worldPoint.getY()
                    && marker.lastSentPlane == worldPoint.getPlane()) {
                types.add(entry.getKey());
            }
        }
        return types;
    }

    public void dropTileMarker(RaidMarkerType type, WorldPoint worldPoint, GroupScapeTrackerConfig config) {
        if (worldPoint == null) {
            return;
        }
        String memberName = localPlayerName();
        if (memberName == null) {
            return;
        }

        clearOwnTileMarker(type, config);

        OwnMarker marker = new OwnMarker(UUID.randomUUID().toString());
        marker.lastSentX = worldPoint.getX();
        marker.lastSentY = worldPoint.getY();
        marker.lastSentPlane = worldPoint.getPlane();
        ownTileMarkers.put(type, marker);

        sendStart(marker.markerId, memberName, type, RaidMarkerState.KIND_TILE,
                worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), null, config);
    }

    public void dropNpcMarker(RaidMarkerType type, NPC npc, GroupScapeTrackerConfig config) {
        WorldPoint worldPoint = npc.getWorldLocation();
        if (worldPoint == null) {
            return;
        }
        String memberName = localPlayerName();
        if (memberName == null) {
            return;
        }

        clearOwnNpcMarker(type, config);

        OwnMarker marker = new OwnMarker(UUID.randomUUID().toString());
        marker.trackedNpc = npc;
        marker.lastUpdateSentAtMs = System.currentTimeMillis();
        marker.lastSentX = worldPoint.getX();
        marker.lastSentY = worldPoint.getY();
        marker.lastSentPlane = worldPoint.getPlane();
        ownNpcMarkers.put(type, marker);

        sendStart(marker.markerId, memberName, type, RaidMarkerState.KIND_NPC,
                worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), npc.getName(), config);
    }

    public void clearOwnTileMarker(RaidMarkerType type, GroupScapeTrackerConfig config) {
        OwnMarker marker = ownTileMarkers.remove(type);
        if (marker != null) {
            sendEnd(marker.markerId, config);
        }
    }

    public void clearOwnNpcMarker(RaidMarkerType type, GroupScapeTrackerConfig config) {
        OwnMarker marker = ownNpcMarkers.remove(type);
        if (marker != null) {
            sendEnd(marker.markerId, config);
        }
    }

    public void clearOwnMarkersOnNpc(NPC npc, GroupScapeTrackerConfig config) {
        for (RaidMarkerType type : ownMarkerTypesOnNpc(npc)) {
            clearOwnNpcMarker(type, config);
        }
    }

    public void clearOwnMarkersOnTile(WorldPoint worldPoint, GroupScapeTrackerConfig config) {
        for (RaidMarkerType type : ownMarkerTypesOnTile(worldPoint)) {
            clearOwnTileMarker(type, config);
        }
    }

    /** Clears every active marker (all 8 slots) at once - used on logout/hop/plugin shutdown, since
     * unlike a ping a marker never auto-expires on its own. */
    public void clearAll(GroupScapeTrackerConfig config) {
        for (RaidMarkerType type : RaidMarkerType.values()) {
            clearOwnTileMarker(type, config);
            clearOwnNpcMarker(type, config);
        }
    }

    /** Called by {@link com.groupscape.GroupScapeTrackerPlugin#onNpcDespawned} for every despawn -
     * ends any marker tracking that specific NPC (death or ordinary despawn both end it). */
    public void onNpcDespawned(NPC npc, GroupScapeTrackerConfig config) {
        clearOwnMarkersOnNpc(npc, config);
    }

    /** No expiry check, unlike {@link PingManager#onGameTick} - only re-sends a live NPC marker's
     * position as it moves. */
    public void onGameTick(GroupScapeTrackerConfig config) {
        long now = System.currentTimeMillis();
        for (OwnMarker marker : ownNpcMarkers.values()) {
            if (marker.trackedNpc == null) {
                continue;
            }
            WorldPoint worldPoint = marker.trackedNpc.getWorldLocation();
            if (worldPoint == null) {
                continue;
            }
            boolean moved = worldPoint.getX() != marker.lastSentX
                    || worldPoint.getY() != marker.lastSentY
                    || worldPoint.getPlane() != marker.lastSentPlane;
            if (moved && now - marker.lastUpdateSentAtMs >= NPC_UPDATE_INTERVAL_MS) {
                marker.lastSentX = worldPoint.getX();
                marker.lastSentY = worldPoint.getY();
                marker.lastSentPlane = worldPoint.getPlane();
                marker.lastUpdateSentAtMs = now;
                sendUpdate(marker.markerId, worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), config);
            }
        }
    }

    public void shutdown() {
        sendExecutor.shutdownNow();
    }

    private String localPlayerName() {
        Player local = client.getLocalPlayer();
        return local == null ? null : local.getName();
    }

    private static class RaidMarkerRequestBody {
        String action;
        String markerId;
        String memberName;
        String markerType;
        String kind;
        Integer x;
        Integer y;
        Integer plane;
        String npcName;
    }

    private void sendStart(String markerId, String memberName, RaidMarkerType type, String kind,
                            int x, int y, int plane, String npcName, GroupScapeTrackerConfig config) {
        RaidMarkerRequestBody body = new RaidMarkerRequestBody();
        body.action = "start";
        body.markerId = markerId;
        body.memberName = memberName;
        body.markerType = type.wireValue;
        body.kind = kind;
        body.x = x;
        body.y = y;
        body.plane = plane;
        body.npcName = npcName;
        send(body, config);
    }

    private void sendUpdate(String markerId, int x, int y, int plane, GroupScapeTrackerConfig config) {
        RaidMarkerRequestBody body = new RaidMarkerRequestBody();
        body.action = "update";
        body.markerId = markerId;
        body.x = x;
        body.y = y;
        body.plane = plane;
        send(body, config);
    }

    private void sendEnd(String markerId, GroupScapeTrackerConfig config) {
        RaidMarkerRequestBody body = new RaidMarkerRequestBody();
        body.action = "end";
        body.markerId = markerId;
        send(body, config);
    }

    /** Fire-and-forget - never called from the client thread's blocking path. */
    private void send(RaidMarkerRequestBody body, GroupScapeTrackerConfig config) {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) {
            log.debug("RaidMarker: not sending {} - apiKey empty={}, accountHash={}", body.action, apiKey.isEmpty(), accountHashValue);
            return;
        }

        String url = httpRequestService.getBaseUrl() + "/api/characters/" + accountHashValue + "/raid-marker";
        sendExecutor.submit(() -> {
            HttpRequestService.HttpResponse response = httpRequestService.post(url, apiKey, body);
            if (!response.isSuccessful()) {
                log.debug("raid marker {} failed: {} {}", body.action, response.getCode(), response.getBody());
            } else {
                log.debug("raid marker {} sent ok (markerId={})", body.action, body.markerId);
            }
        });
    }
}
