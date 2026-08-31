package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Keeps one {@link WorldMapPoint} per eligible group member in sync with {@link RosterState},
 * called from a game tick. A member's name shows as a hover tooltip only - RuneLite's world map
 * has no always-on label rendering for points (its tooltip is a hover-triggered popup, same as
 * quest/clue markers), so this can't diverge from that convention. {@code snapToEdge}/
 * {@code jumpOnClick} give the same off-screen edge arrow (click to pan/zoom into view) that
 * quest and clue markers already use.
 */
public class GroupWorldMapPoints {
    private static final int ICON_HEIGHT = 20;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;
    private final WorldMapPointManager worldMapPointManager;
    private final MemberMapIcons icons;
    private final Map<String, WorldMapPoint> pointsByMember = new HashMap<>();

    public GroupWorldMapPoints(Client client, GroupScapeTrackerConfig config, RosterState rosterState,
                                WorldMapPointManager worldMapPointManager, MemberMapIcons icons) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        this.worldMapPointManager = worldMapPointManager;
        this.icons = icons;
    }

    public void sync() {
        Player localPlayer = client.getLocalPlayer();
        if (!config.mapMarkersEnabled() || localPlayer == null || localPlayer.getName() == null) {
            clear();
            return;
        }

        int world = client.getWorld();
        int plane = client.getPlane();
        String localName = localPlayer.getName();

        Map<String, RosterMember> eligible = new HashMap<>();
        for (RosterMember member : rosterState.all()) {
            if (!member.name.equalsIgnoreCase(localName) && isEligible(member, world, plane)) {
                eligible.put(member.name, member);
            }
        }

        Iterator<Map.Entry<String, WorldMapPoint>> it = pointsByMember.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WorldMapPoint> entry = it.next();
            if (!eligible.containsKey(entry.getKey())) {
                worldMapPointManager.remove(entry.getValue());
                it.remove();
            }
        }

        for (RosterMember member : eligible.values()) {
            WorldPoint worldPoint = new WorldPoint(member.worldX, member.worldY, member.plane);
            WorldMapPoint point = pointsByMember.get(member.name);
            if (point == null) {
                point = new WorldMapPoint(worldPoint, icons.get(member.color, ICON_HEIGHT));
                point.setName(member.name);
                point.setTooltip(member.name);
                point.setSnapToEdge(true);
                point.setJumpOnClick(true);
                pointsByMember.put(member.name, point);
                worldMapPointManager.add(point);
            } else {
                point.setWorldPoint(worldPoint);
                point.setImage(icons.get(member.color, ICON_HEIGHT));
            }
        }
    }

    public void clear() {
        for (WorldMapPoint point : pointsByMember.values()) {
            worldMapPointManager.remove(point);
        }
        pointsByMember.clear();
    }

    private boolean isEligible(RosterMember member, int world, int plane) {
        if (member.worldX == null || member.worldY == null || member.plane == null) {
            return false;
        }
        if (member.world == null || member.world != world || member.plane != plane) {
            return false;
        }
        return !isOffline(member);
    }

    private boolean isOffline(RosterMember member) {
        if (member.lastHeartbeatAt == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= RosterMember.OFFLINE_THRESHOLD_MS;
    }
}
