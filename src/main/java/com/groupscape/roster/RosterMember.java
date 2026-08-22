package com.groupscape.roster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Mutable per-member vitals snapshot the overlay renders from. */
public class RosterMember {
    /** Shared by {@code PartyFrameOverlay} and {@code RosterNotifier} so both agree on "offline". */
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

    public RosterMember(String name) {
        this.name = name;
    }

    public void applyVitals(RosterWireTypes.WireVitals vitals) {
        if (vitals == null) return;

        this.hp = vitals.hp;
        this.maxHp = vitals.maxHp;
        this.prayer = vitals.prayer;
        this.maxPrayer = vitals.maxPrayer;
        this.runEnergy = vitals.runEnergy;
        this.specEnergy = vitals.specEnergy;
        this.world = vitals.world;
        this.targetName = vitals.targetName;
        this.targetHealthRatio = vitals.targetHealthRatio;
        this.targetHealthScale = vitals.targetHealthScale;
        this.activePrayers = vitals.activePrayers != null ? vitals.activePrayers : Collections.emptyList();
        this.richPresence = vitals.richPresence;

        if (vitals.lastHeartbeatAt != null) {
            try {
                this.lastHeartbeatAt = Instant.parse(vitals.lastHeartbeatAt);
            } catch (Exception ignored) {
                this.lastHeartbeatAt = Instant.now();
            }
        } else {
            this.lastHeartbeatAt = Instant.now();
        }
    }
}
