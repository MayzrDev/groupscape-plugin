package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Keeps one {@link WorldMapPoint} per active raid marker in sync with {@link RaidMarkerState},
 * called from a game tick - see {@link PingWorldMapPoints} for the equivalent plain-ping version
 * this mirrors, including why it's deliberately not filtered by world/plane.
 */
public class RaidMarkerWorldMapPoints {
    private static final int ICON_HEIGHT = 24;

    private final GroupScapeTrackerConfig config;
    private final RaidMarkerState raidMarkerState;
    private final WorldMapPointManager worldMapPointManager;
    private final RaidMarkerIcons icons;
    private final Map<String, WorldMapPoint> pointsByMarkerId = new HashMap<>();

    public RaidMarkerWorldMapPoints(GroupScapeTrackerConfig config, RaidMarkerState raidMarkerState,
                                     WorldMapPointManager worldMapPointManager, RaidMarkerIcons icons) {
        this.config = config;
        this.raidMarkerState = raidMarkerState;
        this.worldMapPointManager = worldMapPointManager;
        this.icons = icons;
    }

    public void sync() {
        if (!config.raidMarkersEnabled()) {
            clear();
            return;
        }

        Map<String, RaidMarkerState.ActiveMarker> active = new HashMap<>();
        for (RaidMarkerState.ActiveMarker marker : raidMarkerState.all()) {
            active.put(marker.markerId, marker);
        }

        Iterator<Map.Entry<String, WorldMapPoint>> it = pointsByMarkerId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WorldMapPoint> entry = it.next();
            if (!active.containsKey(entry.getKey())) {
                worldMapPointManager.remove(entry.getValue());
                it.remove();
            }
        }

        for (RaidMarkerState.ActiveMarker marker : active.values()) {
            WorldPoint worldPoint = new WorldPoint(marker.worldX, marker.worldY, marker.plane);
            String tooltip = RaidMarkerMinimapOverlay.markerLabel(marker);

            WorldMapPoint point = pointsByMarkerId.get(marker.markerId);
            if (point == null) {
                point = new WorldMapPoint(worldPoint, icons.get(marker.markerType, ICON_HEIGHT));
                point.setName(tooltip);
                point.setTooltip(tooltip);
                point.setSnapToEdge(true);
                point.setJumpOnClick(true);
                pointsByMarkerId.put(marker.markerId, point);
                worldMapPointManager.add(point);
            } else {
                point.setWorldPoint(worldPoint);
                point.setImage(icons.get(marker.markerType, ICON_HEIGHT));
                point.setTooltip(tooltip);
            }
        }
    }

    public void clear() {
        for (WorldMapPoint point : pointsByMarkerId.values()) {
            worldMapPointManager.remove(point);
        }
        pointsByMarkerId.clear();
    }
}
