package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Draws every active raid marker visible in the local player's game scene: a tile outline in the
 * marker type's color plus the type's badge icon floating above it (see {@link RaidMarkerIcons}),
 * and - only for a tile marker, never an NPC marker - a gradient beam column (see
 * {@link BeamRenderer}). See {@link PingViewportOverlay} for the equivalent plain-ping rendering
 * this mirrors; unlike that overlay a marker never expires, so there's no timeout logic here.
 */
public class RaidMarkerViewportOverlay extends Overlay {
    private static final int ICON_HEIGHT = 32;
    private static final int MARKER_HEIGHT = 170;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RaidMarkerState raidMarkerState;
    private final RaidMarkerIcons icons;

    public RaidMarkerViewportOverlay(Client client, GroupScapeTrackerConfig config,
                                      RaidMarkerState raidMarkerState, RaidMarkerIcons icons) {
        this.client = client;
        this.config = config;
        this.raidMarkerState = raidMarkerState;
        this.icons = icons;
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.raidMarkersEnabled()) {
            return null;
        }

        for (RaidMarkerState.ActiveMarker marker : raidMarkerState.all()) {
            if (marker.plane != client.getPlane()) continue;

            WorldPoint worldPoint = new WorldPoint(marker.worldX, marker.worldY, marker.plane);
            LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
            if (localPoint == null) continue;

            Color color = marker.markerType.color();
            drawTileOutline(graphics, localPoint, color);

            if (RaidMarkerState.KIND_TILE.equals(marker.kind)) {
                Point top = Perspective.localToCanvas(client, localPoint, client.getPlane(), MARKER_HEIGHT);
                Point bottom = Perspective.localToCanvas(client, localPoint, client.getPlane(), 0);
                BeamRenderer.draw(graphics, top, bottom, color);
            }

            drawIcon(graphics, localPoint, marker.markerType);
        }

        return null;
    }

    private void drawTileOutline(Graphics2D graphics, LocalPoint localPoint, Color color) {
        Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);
        if (tilePoly != null) {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(tilePoly);
        }
    }

    private void drawIcon(Graphics2D graphics, LocalPoint localPoint, RaidMarkerType type) {
        Point iconPoint = Perspective.localToCanvas(client, localPoint, client.getPlane(), MARKER_HEIGHT);
        if (iconPoint == null) return;
        BufferedImage icon = icons.get(type, ICON_HEIGHT);
        graphics.drawImage(icon, iconPoint.getX() - icon.getWidth() / 2, iconPoint.getY() - icon.getHeight() / 2, null);
    }
}
