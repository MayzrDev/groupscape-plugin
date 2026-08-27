package com.groupscape.timetracking;

import java.util.Objects;

/** One row of the farming/birdhouse timers payload sent to the server. */
public final class PatchTimerEntry {
    public final String category; // "herb" | "tree" | "fruit_tree" | "hardwood_tree" | "birdhouse"
    public final String label;
    public final String status; // "growing" | "harvestable" | "diseased" | "dead" | "seeded" | "built" | "empty" | "unknown"
    public final Long readyAt; // epoch seconds, null if not applicable
    public final boolean unconfirmed;
    public final Integer produceItemId; // item id of the planted crop, null if unknown/not applicable

    public PatchTimerEntry(String category, String label, String status, Long readyAt, boolean unconfirmed, Integer produceItemId) {
        this.category = category;
        this.label = label;
        this.status = status;
        this.readyAt = readyAt;
        this.unconfirmed = unconfirmed;
        this.produceItemId = produceItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PatchTimerEntry)) return false;
        PatchTimerEntry other = (PatchTimerEntry) o;
        return unconfirmed == other.unconfirmed
                && Objects.equals(category, other.category)
                && Objects.equals(label, other.label)
                && Objects.equals(status, other.status)
                && Objects.equals(readyAt, other.readyAt)
                && Objects.equals(produceItemId, other.produceItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, label, status, readyAt, unconfirmed, produceItemId);
    }
}
