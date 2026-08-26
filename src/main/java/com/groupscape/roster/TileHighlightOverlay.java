package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Outlines the tile of every group member currently rendered in the local player's game scene.
 * {@link Player#getCanvasTilePoly()} already returns null when an actor isn't projectable onto
 * the canvas, so that's used as the "visible on screen" check - no separate occlusion raycasting.
 */
public class TileHighlightOverlay extends Overlay {
    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;

    public TileHighlightOverlay(Client client, GroupScapeTrackerConfig config, RosterState rosterState) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.tileHighlightEnabled()) {
            return null;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        graphics.setStroke(new BasicStroke(config.tileHighlightStrokeWidth()));

        for (Player player : client.getPlayers()) {
            if (player == null || player == localPlayer || player.getName() == null) {
                continue;
            }

            RosterMember member = findMember(player.getName());
            if (member == null) {
                continue;
            }

            Shape tilePoly = player.getCanvasTilePoly();
            if (tilePoly == null) {
                continue;
            }

            graphics.setColor(highlightColor(member.color));
            graphics.draw(tilePoly);
        }

        return null;
    }

    private RosterMember findMember(String playerName) {
        for (RosterMember member : rosterState.all()) {
            if (member.name.equalsIgnoreCase(playerName)) {
                return member;
            }
        }
        return null;
    }

    private Color highlightColor(String hex) {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, config.tileHighlightOpacity())) * 2.55);
        try {
            Color base = Color.decode(hex);
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        } catch (Exception e) {
            return new Color(255, 255, 255, alpha);
        }
    }
}
