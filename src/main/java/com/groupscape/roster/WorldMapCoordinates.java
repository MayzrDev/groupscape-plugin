package com.groupscape.roster;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

/**
 * Reverses {@code WorldMapOverlay.mapWorldPointToGraphicsPoint} (RuneLite has no built-in
 * canvas-to-world helper for the world map - only the forward direction, used for drawing markers)
 * so a right-click on the world map can resolve to a {@link WorldPoint} for a ping. Derived by
 * decompiling that method in {@code client-1.12.37.jar} (javap -c), the same technique used to
 * verify the world-map/minimap member marker overlays - see {@code MinimapLocationOverlay}'s and
 * {@code GroupWorldMapPoints}' doc comments. Worth re-checking against that bytecode if this ever
 * mis-places a ping by a visible amount; it's cosmetic-only (a wrong tile, not a crash) if a RuneLite
 * update shifts the formula.
 */
public final class WorldMapCoordinates {
    /** The world map's map-area widget, {@code group 594 (Worldmap), child 7} - found as the
     * literal RuneLite's own {@code WorldMapOverlay} looks up via {@code getWidget(38993927)}. */
    public static final int WORLD_MAP_VIEW_WIDGET_ID = 38993927;

    private WorldMapCoordinates() {
    }

    /** @return the world point under {@code canvasPoint}, or null if the world map isn't open/ready. */
    public static WorldPoint canvasPointToWorldPoint(Client client, Point canvasPoint) {
        Widget widget = client.getWidget(WORLD_MAP_VIEW_WIDGET_ID);
        if (widget == null) return null;

        float zoom = client.getWorldMap().getWorldMapZoom();
        if (zoom <= 0) return null;

        Rectangle bounds = widget.getBounds();
        int tilesWide = (int) Math.ceil(bounds.getWidth() / zoom);
        int tilesHigh = (int) Math.ceil(bounds.getHeight() / zoom);

        Point mapPosition = client.getWorldMap().getWorldMapPosition();

        int relX = canvasPoint.getX() - bounds.x;
        int relY = canvasPoint.getY() - bounds.y;

        double halfZoomCeil = Math.ceil(zoom / 2.0);
        double xPixels = relX - halfZoomCeil + zoom;
        double yPixels = bounds.getHeight() - relY;
        yPixels = yPixels + zoom + halfZoomCeil;

        double tileOffsetX = xPixels / zoom;
        double tileOffsetY = yPixels / zoom;

        int worldX = (int) Math.round(tileOffsetX - tilesWide / 2 + mapPosition.getX());
        int y9 = mapPosition.getY() - tilesHigh / 2;
        int worldY = (int) Math.round(tileOffsetY + y9 - 1);

        return new WorldPoint(worldX, worldY, client.getPlane());
    }
}
