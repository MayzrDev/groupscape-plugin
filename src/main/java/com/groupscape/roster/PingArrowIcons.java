package com.groupscape.roster;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches the downward arrow/pin glyph used for ping markers on the minimap and world
 * map - deliberately a different silhouette from {@link MemberMapIcons}' helmet glyph so a ping
 * never reads as a member-position marker at a glance. Tinted per-pinger via the same hue as their
 * assigned color (see {@link com.groupscape.roster.RosterMember#color}).
 */
public class PingArrowIcons {
    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    /** @param heightPx target rendered height, arrowhead included. */
    public BufferedImage get(String hexColor, int heightPx) {
        String key = hexColor + "@" + heightPx;
        return cache.computeIfAbsent(key, k -> render(hexColor, heightPx));
    }

    private static BufferedImage render(String hexColor, int heightPx) {
        Color fill;
        try {
            fill = Color.decode(hexColor);
        } catch (Exception e) {
            fill = Color.WHITE;
        }

        int width = Math.max(1, Math.round(heightPx * 0.85f));
        BufferedImage image = new BufferedImage(width, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int headHeight = Math.round(heightPx * 0.55f);
        int shaftWidth = Math.max(1, Math.round(width * 0.35f));
        int shaftX = (width - shaftWidth) / 2;

        Polygon arrow = new Polygon();
        arrow.addPoint(width / 2, heightPx - 1);
        arrow.addPoint(0, heightPx - 1 - headHeight);
        arrow.addPoint(shaftX, heightPx - 1 - headHeight);
        arrow.addPoint(shaftX, 0);
        arrow.addPoint(shaftX + shaftWidth, 0);
        arrow.addPoint(shaftX + shaftWidth, heightPx - 1 - headHeight);
        arrow.addPoint(width, heightPx - 1 - headHeight);

        g.setColor(fill);
        g.fillPolygon(arrow);
        g.setColor(Color.BLACK);
        g.drawPolygon(arrow);
        g.dispose();

        return image;
    }
}
