package com.groupscape.roster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Mutable per-member inventory/equipment/skills snapshot the sidepanel renders from, filled by
 * {@link GroupSnapshotClient}'s periodic delta poll. Mirrors {@link RosterMember}'s
 * apply-only-what-changed pattern: the server only sends a field when it changed since the
 * poll's {@code from_time} cursor, so a field null in one response just means "unchanged", not
 * "cleared" - go on displaying the last known value.
 */
public class GroupSnapshotMember {
    public final String name;
    public String color;
    public List<Integer> inventory = Collections.emptyList();
    public List<Integer> equipment = Collections.emptyList();
    public List<Integer> skillXp = Collections.emptyList();
    public Instant lastUpdated;

    public GroupSnapshotMember(String name) {
        this.name = name;
    }

    public void applyDelta(GroupSnapshotWireTypes.GroupMemberWire wire) {
        if (wire == null) return;

        if (wire.color != null) color = wire.color;
        if (wire.inventory != null) inventory = wire.inventory;
        if (wire.equipment != null) equipment = wire.equipment;
        if (wire.skills != null) skillXp = wire.skills;

        if (wire.last_updated != null) {
            try {
                lastUpdated = Instant.parse(wire.last_updated);
            } catch (Exception ignored) {
                // leave the previous value in place
            }
        }
    }
}
