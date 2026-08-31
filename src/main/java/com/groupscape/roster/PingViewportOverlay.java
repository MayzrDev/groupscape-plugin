package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
 * Draws every active ping visible in the local player's game scene: both a tile ping and an NPC
 * ping outline the ground tile itself (not the NPC's 3D model hull - a moving NPC's hull is a
 * poor match for "the tile RuneLite says it's on" a tick later anyway), a tile ping also gets a
 * vertical beam, and an NPC ping gets an arrow floating at a fixed height above the tile.
 */
public class PingViewportOverlay extends Overlay {
    private static final int BEAM_HEIGHT = 140;
    private static final int NPC_ARROW_HEIGHT = 190;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final PingState pingState;
    private final RosterState rosterState;

    public PingViewportOverlay(Client client, GroupScapeTrackerConfig config, PingState pingState, RosterState rosterState) {
        this.client = client;
        this.config = config;
        this.pingState = pingState;
        this.rosterState = rosterState;
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.pingsEnabled()) {
            return null;
        }

        for (PingState.ActivePing ping : pingState.all()) {
            if (ping.plane != client.getPlane()) continue;

            WorldPoint worldPoint = new WorldPoint(ping.worldX, ping.worldY, ping.plane);
            LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
            if (localPoint == null) continue;

            Color color = colorFor(ping.memberName);
            drawTileOutline(graphics, localPoint, color);

            if (PingState.KIND_NPC.equals(ping.kind)) {
                drawArrowAt(graphics, localPoint, NPC_ARROW_HEIGHT, color);
            } else {
                drawBeamAndArrow(graphics, localPoint, BEAM_HEIGHT, color);
            }
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

    private void drawArrowAt(Graphics2D graphics, LocalPoint localPoint, int height, Color color) {
        Point arrowPoint = Perspective.localToCanvas(client, localPoint, client.getPlane(), height);
        if (arrowPoint != null) {
            drawArrowGlyph(graphics, arrowPoint.getX(), arrowPoint.getY(), color);
        }
    }

    private void drawBeamAndArrow(Graphics2D graphics, LocalPoint localPoint, int beamHeight, Color color) {
        Point top = Perspective.localToCanvas(client, localPoint, client.getPlane(), beamHeight);
        Point bottom = Perspective.localToCanvas(client, localPoint, client.getPlane(), 0);
        if (top == null || bottom == null) return;

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(3));
        graphics.drawLine(top.getX(), top.getY(), bottom.getX(), bottom.getY());
        drawArrowGlyph(graphics, top.getX(), top.getY(), color);
    }

    /** A small downward-pointing arrow, tip at {@code (tipX, tipY)} - same motif as the
     * minimap/world map/web markers ({@link PingArrowIcons}), just drawn directly instead of
     * cached as an image since a viewport marker's screen position changes every frame. */
    private void drawArrowGlyph(Graphics2D graphics, int tipX, int tipY, Color color) {
        int headWidth = 13;
        int headHeight = 8;
        int shaftWidth = 5;
        int shaftHeight = 12;

        Polygon arrow = new Polygon();
        arrow.addPoint(tipX, tipY);
        arrow.addPoint(tipX - headWidth / 2, tipY - headHeight);
        arrow.addPoint(tipX - shaftWidth / 2, tipY - headHeight);
        arrow.addPoint(tipX - shaftWidth / 2, tipY - headHeight - shaftHeight);
        arrow.addPoint(tipX + shaftWidth / 2, tipY - headHeight - shaftHeight);
        arrow.addPoint(tipX + shaftWidth / 2, tipY - headHeight);
        arrow.addPoint(tipX + headWidth / 2, tipY - headHeight);

        graphics.setColor(color);
        graphics.fillPolygon(arrow);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(1));
        graphics.drawPolygon(arrow);
    }

    private Color colorFor(String memberName) {
        RosterMember member = rosterState.findByName(memberName);
        String hex = member != null ? member.color : "#808080";
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
}
