package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kill/loot/death aren't per-tick snapshot data like the rest of {@link DataManager}'s
 * {@link DataState} fields - they're discrete events detected between uploads, so this
 * accumulates them into a list (mirroring {@link DepositedItems}'s accumulator pattern
 * rather than DataState's latest-value-wins one) and drains everything buffered since the
 * last upload into the "events" key on the next {@code consumeState}.
 *
 * Kill+loot correlation is best-effort only, ported from groupscape-old: a kill is held
 * here and a same-NPC-name loot arriving before the next drain gets attached to it; if loot
 * never arrives in time the kill still ships without it rather than being held back. Must
 * only be touched from the client thread.
 */
public class KillLootDeathEvents {
    private final List<PendingKill> pendingKills = new ArrayList<>();
    private final List<Map<String, Object>> pendingDeaths = new ArrayList<>();
    private String owner;

    private List<PendingKill> consumedKills;
    private List<Map<String, Object>> consumedDeaths;
    private String consumedOwner;

    private static final class PendingKill {
        final int npcId;
        final String npcName;
        final int worldX;
        final int worldY;
        final int plane;
        final int world;
        List<Map<String, Object>> loot;

        PendingKill(int npcId, String npcName, int worldX, int worldY, int plane, int world) {
            this.npcId = npcId;
            this.npcName = npcName;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.world = world;
        }

        Map<String, Object> toMap() {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "kill");
            event.put("npcId", npcId);
            event.put("npcName", npcName);
            event.put("worldX", worldX);
            event.put("worldY", worldY);
            event.put("plane", plane);
            event.put("world", world);
            if (loot != null) {
                event.put("loot", loot);
            }
            return event;
        }
    }

    public synchronized void onKill(String playerName, int npcId, String npcName, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        pendingKills.add(new PendingKill(npcId, npcName, worldX, worldY, plane, world));
    }

    /**
     * Attaches loot to the most recently-queued still-unloot-attached pending kill for this
     * NPC name. Matching by name (not id) since {@code LootReceived} exposes the NPC's
     * composition name, not necessarily the same id {@code NpcDespawned} reported for
     * multi-form bosses.
     */
    public synchronized void onLoot(String npcName, List<Map<String, Object>> items) {
        for (int i = pendingKills.size() - 1; i >= 0; i--) {
            PendingKill kill = pendingKills.get(i);
            if (kill.npcName.equals(npcName) && kill.loot == null) {
                kill.loot = items;
                return;
            }
        }
        // No matching pending kill - dropped, not retried or queued (see class doc).
    }

    public synchronized void onDeath(String playerName, int worldX, int worldY, int plane, int world, String killerName) {
        owner = playerName;
        Map<String, Object> death = new HashMap<>();
        death.put("type", "death");
        death.put("worldX", worldX);
        death.put("worldY", worldY);
        death.put("plane", plane);
        death.put("world", world);
        if (killerName != null) {
            death.put("killerName", killerName);
        }
        pendingDeaths.add(death);
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pendingKills.isEmpty() && pendingDeaths.isEmpty()) return;

        String whoIsUpdating = (String) output.get("name");
        if (owner != null && owner.equals(whoIsUpdating)) {
            List<Map<String, Object>> events = new ArrayList<>();
            for (PendingKill kill : pendingKills) {
                events.add(kill.toMap());
            }
            events.addAll(pendingDeaths);
            output.put("events", events);
        }

        consumedKills = pendingKills.isEmpty() ? null : new ArrayList<>(pendingKills);
        consumedDeaths = pendingDeaths.isEmpty() ? null : new ArrayList<>(pendingDeaths);
        consumedOwner = owner;
        pendingKills.clear();
        pendingDeaths.clear();
        owner = null;
    }

    public synchronized void restoreState() {
        if (consumedKills == null && consumedDeaths == null) return;

        if (consumedKills != null) {
            pendingKills.addAll(0, consumedKills);
        }
        if (consumedDeaths != null) {
            pendingDeaths.addAll(0, consumedDeaths);
        }
        if (owner == null) {
            owner = consumedOwner;
        }
        consumedKills = null;
        consumedDeaths = null;
        consumedOwner = null;
    }
}
