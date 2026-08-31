package com.groupscape.roster;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import net.runelite.api.Point;

/**
 * Shared Ground-Items-plugin-style gradient beam column - a vertical line fading from transparent
 * at the top to solid at the ground, drawn in addition to (not instead of) each overlay's own
 * marker glyph. Used by both {@link PingViewportOverlay} (per-pinger hue) and
 * {@link RaidMarkerViewportOverlay} (per-type color) - in both cases only for a tile ping/marker,
 * never an NPC one.
 */
final class BeamRenderer {
    private static final int WIDTH_PX = 8;
    private static final int TOP_ALPHA = 0;
    private static final int BOTTOM_ALPHA = 160;

    private BeamRenderer() {
    }

    static void draw(Graphics2D graphics, Point top, Point bottom, Color color) {
        if (top == null || bottom == null) {
            return;
        }

        Color transparent = new Color(color.getRed(), color.getGreen(), color.getBlue(), TOP_ALPHA);
        Color solid = new Color(color.getRed(), color.getGreen(), color.getBlue(), BOTTOM_ALPHA);
        Paint originalPaint = graphics.getPaint();
        graphics.setPaint(new GradientPaint(top.getX(), top.getY(), transparent, bottom.getX(), bottom.getY(), solid));
        graphics.setStroke(new BasicStroke(WIDTH_PX));
        graphics.drawLine(top.getX(), top.getY(), bottom.getX(), bottom.getY());
        graphics.setPaint(originalPaint);
    }
}
