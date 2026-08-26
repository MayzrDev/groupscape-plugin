package com.groupscape.roster;

import java.util.List;

/**
 * DTOs mirroring the server's WebSocket wire format
 * (`groupscape-web/server/src/websocket.rs`). Field names match the JSON
 * keys exactly (server serializes camelCase) so Gson's default reflection
 * can deserialize without extra annotations.
 */
public class RosterWireTypes {
    public static class Envelope {
        public String type;
        public com.google.gson.JsonObject payload;
        public String ts;
    }

    public static class RosterSnapshotPayload {
        public List<RosterMemberEntry> roster;
    }

    public static class RosterMemberEntry {
        public String name;
        public String color;
        public WireVitals vitals;
    }

    public static class VitalsUpdatePayload {
        public String name;
        public WireVitals vitals;
    }

    public static class KillEventPayload {
        public String memberName;
        public String npcName;
    }

    public static class DropEventPayload {
        public String memberName;
        public String message;
    }

    public static class ColorUpdatePayload {
        public String name;
        public String color;
    }

    public static class WireVitals {
        public Integer hp;
        public Integer maxHp;
        public Integer prayer;
        public Integer maxPrayer;
        public Integer runEnergy;
        public Integer specEnergy;
        public Integer world;
        public String lastHeartbeatAt;
        public String targetName;
        public Integer targetHealthRatio;
        public Integer targetHealthScale;
        public List<String> activePrayers;
        public String richPresence;
    }
}
