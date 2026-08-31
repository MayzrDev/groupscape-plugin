package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws each visible group member's hue-tinted marker on the minimap, with the member's name
 * shown as a hover-only tooltip (the world map equivalent, {@link GroupWorldMapPoints}, labels
 * always-on instead - minimap space is tighter). Only members RuneLite has actually loaded as
 * on-screen {@link Player} actors can appear here, which already limits this to members in the
 * local player's world and on their plane.
 */
public class MinimapLocationOverlay extends Overlay {
    private static final int ICON_HEIGHT = 12;
    private static final int HOVER_RADIUS_PX = 7;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;
    private final TooltipManager tooltipManager;
    private final MemberMapIcons icons;

    public MinimapLocationOverlay(Client client, GroupScapeTrackerConfig config, RosterState rosterState,
                                   TooltipManager tooltipManager, MemberMapIcons icons) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        this.tooltipManager = tooltipManager;
        this.icons = icons;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.mapMarkersEnabled()) {
            return null;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        Point mouse = client.getMouseCanvasPosition();

        for (Player player : client.getPlayers()) {
            if (player == null || player == localPlayer || player.getName() == null) {
                continue;
            }

            RosterMember member = findMember(player.getName());
            if (member == null || isOffline(member)) {
                continue;
            }

            Point loc = player.getMinimapLocation();
            if (loc == null) {
                continue;
            }

            BufferedImage icon = icons.get(member.color, ICON_HEIGHT);
            graphics.drawImage(icon, loc.getX() - icon.getWidth() / 2, loc.getY() - icon.getHeight() / 2, null);

            if (mouse != null && mouse.distanceTo(loc) <= HOVER_RADIUS_PX) {
                tooltipManager.add(new Tooltip(member.name));
            }
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

    private boolean isOffline(RosterMember member) {
        if (member.lastHeartbeatAt == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= RosterMember.OFFLINE_THRESHOLD_MS;
    }
}
