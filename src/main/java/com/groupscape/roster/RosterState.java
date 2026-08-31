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

    /** Case-insensitive - matches how {@code MinimapLocationOverlay}/{@code TileHighlightOverlay} already look up a member by their {@code Player} actor's name. */
    public synchronized RosterMember findByName(String name) {
        for (RosterMember member : members.values()) {
            if (member.name.equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    public synchronized void clear() {
        members.clear();
    }
}
