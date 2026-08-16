package com.groupscape.roster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe in-memory roster: written from the WebSocket callback thread, read from the overlay render thread. */
public class RosterState {
    private final Map<String, RosterMember> members = new LinkedHashMap<>();

    public synchronized void replaceAll(List<RosterWireTypes.RosterMemberEntry> roster) {
        members.clear();
        if (roster == null) return;

        for (RosterWireTypes.RosterMemberEntry entry : roster) {
            RosterMember member = new RosterMember(entry.name);
            member.color = entry.color != null ? entry.color : member.color;
            member.applyVitals(entry.vitals);
            members.put(entry.name, member);
        }
    }

    public synchronized RosterMember getOrCreate(String name) {
        return members.computeIfAbsent(name, RosterMember::new);
    }

    public synchronized List<RosterMember> all() {
        return new ArrayList<>(members.values());
    }

    public synchronized void clear() {
        members.clear();
    }
}
