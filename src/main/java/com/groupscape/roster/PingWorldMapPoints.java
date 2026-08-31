package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Keeps one {@link WorldMapPoint} per active ping in sync with {@link PingState}, called from a
 * game tick - same pattern as {@link GroupWorldMapPoints}, but keyed by ping id instead of member
 * name and deliberately not filtered by world/plane: unlike a member's live position, a ping's
 * whole point is "meet/look here", which is still useful to see even if you're not currently on
 * the same world as the pinger.
 */
public class PingWorldMapPoints {
    private static final int ICON_HEIGHT = 20;

    private final GroupScapeTrackerConfig config;
    private final PingState pingState;
    private final RosterState rosterState;
    private final WorldMapPointManager worldMapPointManager;
    private final PingArrowIcons icons;
    private final Map<String, WorldMapPoint> pointsByPingId = new HashMap<>();

    public PingWorldMapPoints(GroupScapeTrackerConfig config, PingState pingState, RosterState rosterState,
                               WorldMapPointManager worldMapPointManager, PingArrowIcons icons) {
        this.config = config;
        this.pingState = pingState;
        this.rosterState = rosterState;
        this.worldMapPointManager = worldMapPointManager;
        this.icons = icons;
    }

    public void sync() {
        if (!config.pingsEnabled()) {
            clear();
            return;
        }

        Map<String, PingState.ActivePing> active = new HashMap<>();
        for (PingState.ActivePing ping : pingState.all()) {
            active.put(ping.pingId, ping);
        }

        Iterator<Map.Entry<String, WorldMapPoint>> it = pointsByPingId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WorldMapPoint> entry = it.next();
            if (!active.containsKey(entry.getKey())) {
                worldMapPointManager.remove(entry.getValue());
                it.remove();
            }
        }

        for (PingState.ActivePing ping : active.values()) {
            WorldPoint worldPoint = new WorldPoint(ping.worldX, ping.worldY, ping.plane);
            RosterMember member = rosterState.findByName(ping.memberName);
            String hex = member != null ? member.color : "#808080";
            String tooltip = ping.npcName != null ? ping.memberName + " pinged " + ping.npcName : ping.memberName + " pinged here";

            WorldMapPoint point = pointsByPingId.get(ping.pingId);
            if (point == null) {
                point = new WorldMapPoint(worldPoint, icons.get(hex, ICON_HEIGHT));
                point.setName(tooltip);
                point.setTooltip(tooltip);
                point.setSnapToEdge(true);
                point.setJumpOnClick(true);
                pointsByPingId.put(ping.pingId, point);
                worldMapPointManager.add(point);
            } else {
                point.setWorldPoint(worldPoint);
                point.setImage(icons.get(hex, ICON_HEIGHT));
                point.setTooltip(tooltip);
            }
        }
    }

    public void clear() {
        for (WorldMapPoint point : pointsByPingId.values()) {
            worldMapPointManager.remove(point);
        }
        pointsByPingId.clear();
    }
}
