package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws every active raid marker's badge icon on the minimap - see {@link PingMinimapOverlay} for
 * the equivalent plain-ping rendering this mirrors. Only markers whose tile is currently within
 * the loaded scene resolve a minimap position, same scoping the ping version gets.
 */
public class RaidMarkerMinimapOverlay extends Overlay {
    private static final int ICON_HEIGHT = 16;
    private static final int HOVER_RADIUS_PX = 8;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RaidMarkerState raidMarkerState;
    private final TooltipManager tooltipManager;
    private final RaidMarkerIcons icons;

    public RaidMarkerMinimapOverlay(Client client, GroupScapeTrackerConfig config, RaidMarkerState raidMarkerState,
                                     TooltipManager tooltipManager, RaidMarkerIcons icons) {
        this.client = client;
        this.config = config;
        this.raidMarkerState = raidMarkerState;
        this.tooltipManager = tooltipManager;
        this.icons = icons;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.raidMarkersEnabled()) {
            return null;
        }

        Point mouse = client.getMouseCanvasPosition();

        for (RaidMarkerState.ActiveMarker marker : raidMarkerState.all()) {
            if (marker.plane != client.getPlane()) continue;

            LocalPoint localPoint = LocalPoint.fromWorld(client, new WorldPoint(marker.worldX, marker.worldY, marker.plane));
            if (localPoint == null) continue;

            Point loc = Perspective.localToMinimap(client, localPoint);
            if (loc == null) continue;

            BufferedImage icon = icons.get(marker.markerType, ICON_HEIGHT);
            graphics.drawImage(icon, loc.getX() - icon.getWidth() / 2, loc.getY() - icon.getHeight() / 2, null);

            if (mouse != null && mouse.distanceTo(loc) <= HOVER_RADIUS_PX) {
                tooltipManager.add(new Tooltip(markerLabel(marker)));
            }
        }

        return null;
    }

    static String markerLabel(RaidMarkerState.ActiveMarker marker) {
        return marker.npcName != null
                ? marker.memberName + "'s " + marker.markerType.displayName + " on " + marker.npcName
                : marker.memberName + "'s " + marker.markerType.displayName;
    }
}
