package com.groupscape.roster;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every group member's currently active raid markers, keyed by marker id, as reported over the
 * party overlay WebSocket - see {@link PingState} for the equivalent plain-ping version this
 * mirrors. Unlike a ping, a marker never auto-expires, so this only ever changes on an explicit
 * start/update/end.
 */
public class RaidMarkerState {
    public static final String KIND_TILE = "tile";
    public static final String KIND_NPC = "npc";

    public static class ActiveMarker {
        public final String markerId;
        public final String memberName;
        public final RaidMarkerType markerType;
        public final String kind;
        public final String npcName;
        public volatile int worldX;
        public volatile int worldY;
        public volatile int plane;

        ActiveMarker(String markerId, String memberName, RaidMarkerType markerType, String kind,
                     int worldX, int worldY, int plane, String npcName) {
            this.markerId = markerId;
            this.memberName = memberName;
            this.markerType = markerType;
            this.kind = kind;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.npcName = npcName;
        }
    }

    private final Map<String, ActiveMarker> markersById = new ConcurrentHashMap<>();

    public void start(RosterWireTypes.RaidMarkerStartPayload payload) {
        RaidMarkerType type = RaidMarkerType.fromWireValue(payload.markerType);
        if (type == null) {
            return;
        }
        markersById.put(payload.markerId,
                new ActiveMarker(payload.markerId, payload.memberName, type, payload.kind,
                        payload.x, payload.y, payload.plane, payload.npcName));
    }

    public void update(RosterWireTypes.RaidMarkerUpdatePayload payload) {
        ActiveMarker marker = markersById.get(payload.markerId);
        if (marker != null) {
            marker.worldX = payload.x;
            marker.worldY = payload.y;
            marker.plane = payload.plane;
        }
    }

    public void end(RosterWireTypes.RaidMarkerEndPayload payload) {
        markersById.remove(payload.markerId);
    }

    public Collection<ActiveMarker> all() {
        return markersById.values();
    }

    public void clear() {
        markersById.clear();
    }
}
