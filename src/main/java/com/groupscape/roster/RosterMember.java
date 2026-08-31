package com.groupscape.roster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Mutable per-member vitals snapshot the overlay renders from. */
public class RosterMember {
    /**
     * Shared by {@code PartyFrameOverlay}, {@code RosterListPanel}, {@code MinimapLocationOverlay},
     * {@code GroupWorldMapPoints}, and {@code RosterNotifier} so they all agree on "offline".
     *
     * <p>Must stay comfortably above {@code DataManager.HEARTBEAT_INTERVAL_MILLIS} (30s) - a
     * member's idle heartbeat lands roughly every 30-31s (1s poll granularity) plus whatever
     * network latency, so a threshold equal to the interval left no margin: any jitter tipped an
     * online member into "offline" for a chunk of every cycle. 60s matches the margin the web
     * app's own online badge already uses ({@code admin_account_is_online_column} in db.rs).
     */
    public static final long OFFLINE_THRESHOLD_MS = 60_000;

    public final String name;
    public String color = "#808080";

    public Integer hp;
    public Integer maxHp;
    public Integer prayer;
    public Integer maxPrayer;
    public Integer runEnergy;
    public Integer specEnergy;
    public Integer world;
    public Instant lastHeartbeatAt;
    public String targetName;
    public Integer targetHealthRatio;
    public Integer targetHealthScale;
    public List<String> activePrayers = Collections.emptyList();
    public String richPresence;
    public Integer worldX;
    public Integer worldY;
    public Integer plane;

    public RosterMember(String name) {
        this.name = name;
    }

    public void applyVitals(RosterWireTypes.WireVitals vitals) {
        if (vitals == null) return;

        // The server sends hp/maxHp/prayer/maxPrayer/runEnergy/world as one all-or-nothing bundle,
        // going all-null for a single missed heartbeat tick. Only overwrite them together when the
        // bundle actually has data, so one missed heartbeat doesn't blank the overlay instantly -
        // stale values persist until isOffline() trips past OFFLINE_THRESHOLD_MS.
        if (vitals.hp != null || vitals.maxHp != null) {
            this.hp = vitals.hp;
            this.maxHp = vitals.maxHp;
            this.prayer = vitals.prayer;
            this.maxPrayer = vitals.maxPrayer;
            this.runEnergy = vitals.runEnergy;
            this.world = vitals.world;
        }
        this.specEnergy = vitals.specEnergy;
        this.targetName = vitals.targetName;
        this.targetHealthRatio = vitals.targetHealthRatio;
        this.targetHealthScale = vitals.targetHealthScale;
        this.activePrayers = vitals.activePrayers != null ? vitals.activePrayers : Collections.emptyList();
        this.richPresence = vitals.richPresence;

        // Sent independently of the hp/prayer bundle above - a heartbeat can carry a position
        // update without touching vitals, so this isn't gated by the same all-or-nothing check.
        if (vitals.coordinates != null && vitals.coordinates.size() >= 3) {
            this.worldX = vitals.coordinates.get(0);
            this.worldY = vitals.coordinates.get(1);
            this.plane = vitals.coordinates.get(2);
        }

        if (vitals.lastHeartbeatAt != null) {
            try {
                this.lastHeartbeatAt = Instant.parse(vitals.lastHeartbeatAt);
            } catch (Exception ignored) {
                this.lastHeartbeatAt = null;
            }
        } else {
            // No heartbeat on record for this member (e.g. they've never sent vitals) -
            // null means offline (see isOffline()), not "just seen".
            this.lastHeartbeatAt = null;
        }
    }
}
