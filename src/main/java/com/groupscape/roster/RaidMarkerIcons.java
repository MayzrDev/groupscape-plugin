package com.groupscape.roster;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.util.ImageUtil;

/**
 * Builds and caches the badge glyph for a raid marker type: the type's real OSRS wiki icon (or, for
 * a type with no wiki icon - see {@link RaidMarkerType#iconResource} - its {@link
 * RaidMarkerType#displayName} drawn as a bold text glyph) centered on a colored circle, used
 * identically in the viewport, minimap, and world map overlays so a marker reads the same
 * everywhere - unlike plain pings, which are only ever tinted by pinger color (see {@link
 * PingArrowIcons}), a raid marker's whole point is being recognizable by type at a glance.
 */
public class RaidMarkerIcons {
    private static final Map<RaidMarkerType, BufferedImage> BASE_ICONS = new EnumMap<>(RaidMarkerType.class);

    static {
        for (RaidMarkerType type : RaidMarkerType.values()) {
            if (type.iconResource != null) {
                BASE_ICONS.put(type, ImageUtil.loadImageResource(RaidMarkerIcons.class, type.iconResource));
            }
        }
    }

    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    /** @param heightPx target rendered badge diameter. */
    public BufferedImage get(RaidMarkerType type, int heightPx) {
        String key = type.name() + "@" + heightPx;
        return cache.computeIfAbsent(key, k -> render(type, heightPx));
    }

    private static BufferedImage render(RaidMarkerType type, int heightPx) {
        BufferedImage badge = new BufferedImage(heightPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = badge.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = type.color();
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 210));
        g.fillOval(0, 0, heightPx - 1, heightPx - 1);
        g.setColor(Color.BLACK);
        g.drawOval(0, 0, heightPx - 1, heightPx - 1);

        BufferedImage base = BASE_ICONS.get(type);
        if (base != null) {
            int iconSize = Math.max(1, Math.round(heightPx * 0.6f));
            int offset = (heightPx - iconSize) / 2;
            g.drawImage(base, offset, offset, iconSize, iconSize, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(g.getFont().deriveFont(Font.BOLD, heightPx * 0.55f));
            g.setColor(Color.WHITE);
            FontMetrics metrics = g.getFontMetrics();
            String glyph = type.displayName;
            int textX = (heightPx - metrics.stringWidth(glyph)) / 2;
            int textY = (heightPx - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(glyph, textX, textY);
        }
        g.dispose();

        return badge;
    }
}
