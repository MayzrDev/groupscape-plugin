package com.groupscape.roster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe in-memory store of inventory/equipment/skills per group member, written from
 * {@link GroupSnapshotClient}'s scheduled poll thread and read from the sidepanel's Swing
 * refresh timer. Mirrors {@link RosterState}'s shape, kept as a separate store rather than
 * folded into {@link RosterMember} since it's fed by a slower HTTP poll instead of the live
 * vitals websocket.
 */
public class GroupSnapshotState {
    private final Map<String, GroupSnapshotMember> members = new LinkedHashMap<>();

    public synchronized void applyDelta(List<GroupSnapshotWireTypes.GroupMemberWire> wire) {
        if (wire == null) return;

        for (GroupSnapshotWireTypes.GroupMemberWire entry : wire) {
            if (entry.name == null) continue;
            GroupSnapshotMember member = members.computeIfAbsent(entry.name, GroupSnapshotMember::new);
            member.applyDelta(entry);
        }
    }

    public synchronized GroupSnapshotMember get(String name) {
        return members.get(name);
    }

    public synchronized List<GroupSnapshotMember> all() {
        return new ArrayList<>(members.values());
    }

    public synchronized void clear() {
        members.clear();
    }
}
