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
 * Draws every active ping's arrow marker on the minimap, hue-tinted per pinger. Only pings whose
 * tile is currently within the loaded scene resolve a minimap position ({@link LocalPoint#fromWorld}
 * returns null otherwise), which naturally limits this to pings near the local player - same
 * scoping {@link MinimapLocationOverlay} gets for free from RuneLite's own {@code Player} actors.
 */
public class PingMinimapOverlay extends Overlay {
    private static final int ICON_HEIGHT = 14;
    private static final int HOVER_RADIUS_PX = 8;

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final PingState pingState;
    private final RosterState rosterState;
    private final TooltipManager tooltipManager;
    private final PingArrowIcons icons;

    public PingMinimapOverlay(Client client, GroupScapeTrackerConfig config, PingState pingState,
                               RosterState rosterState, TooltipManager tooltipManager, PingArrowIcons icons) {
        this.client = client;
        this.config = config;
        this.pingState = pingState;
        this.rosterState = rosterState;
        this.tooltipManager = tooltipManager;
        this.icons = icons;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.pingsEnabled()) {
            return null;
        }

        Point mouse = client.getMouseCanvasPosition();

        for (PingState.ActivePing ping : pingState.all()) {
            if (ping.plane != client.getPlane()) continue;

            LocalPoint localPoint = LocalPoint.fromWorld(client, new WorldPoint(ping.worldX, ping.worldY, ping.plane));
            if (localPoint == null) continue;

            Point loc = Perspective.localToMinimap(client, localPoint);
            if (loc == null) continue;

            RosterMember member = rosterState.findByName(ping.memberName);
            String hex = member != null ? member.color : "#808080";
            BufferedImage icon = icons.get(hex, ICON_HEIGHT);
            graphics.drawImage(icon, loc.getX() - icon.getWidth() / 2, loc.getY() - icon.getHeight(), null);

            if (mouse != null && mouse.distanceTo(loc) <= HOVER_RADIUS_PX) {
                String label = ping.npcName != null ? ping.memberName + " pinged " + ping.npcName : ping.memberName + " pinged here";
                tooltipManager.add(new Tooltip(label));
            }
        }

        return null;
    }
}
