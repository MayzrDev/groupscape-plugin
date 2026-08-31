package com.groupscape.roster;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every group member's currently active ping (right-click/hotkey on an NPC or tile), keyed by
 * ping id, as reported over the party overlay WebSocket. {@link RosterClient.PingEventListener}
 * writes to this; the minimap/world-map/viewport overlays read from it. Includes the local
 * player's own ping too - the server relays everything it publishes back to the sender as well,
 * so this is the single source of truth for "what pings are currently active", not just other
 * members'.
 */
public class PingState {
    public static final String KIND_TILE = "tile";
    public static final String KIND_NPC = "npc";

    public static class ActivePing {
        public final String pingId;
        public final String memberName;
        public final String kind;
        public final String npcName;
        public volatile int worldX;
        public volatile int worldY;
        public volatile int plane;

        ActivePing(String pingId, String memberName, String kind, int worldX, int worldY, int plane, String npcName) {
            this.pingId = pingId;
            this.memberName = memberName;
            this.kind = kind;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.npcName = npcName;
        }
    }

    private final Map<String, ActivePing> pingsById = new ConcurrentHashMap<>();

    public void start(RosterWireTypes.PingStartPayload payload) {
        pingsById.put(payload.pingId,
                new ActivePing(payload.pingId, payload.memberName, payload.kind, payload.x, payload.y, payload.plane, payload.npcName));
    }

    public void update(RosterWireTypes.PingUpdatePayload payload) {
        ActivePing ping = pingsById.get(payload.pingId);
        if (ping != null) {
            ping.worldX = payload.x;
            ping.worldY = payload.y;
            ping.plane = payload.plane;
        }
    }

    public void end(RosterWireTypes.PingEndPayload payload) {
        pingsById.remove(payload.pingId);
    }

    public Collection<ActivePing> all() {
        return pingsById.values();
    }

    public void clear() {
        pingsById.clear();
    }
}
