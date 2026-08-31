package com.groupscape.roster;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.util.ImageUtil;

/**
 * Builds and caches the group map marker glyph: the same {@code player-icon.webp} sprite the
 * web app's canvas-map uses for its "Helm & Ring" marker design, recolored to a member's assigned
 * hex color. AWT has no equivalent to CSS's {@code hue-rotate}/{@code saturate} filter the web
 * side uses, so this recolors each pixel directly - target hue/saturation, keeping the source
 * pixel's brightness (and alpha) - which lands the same for a single-hue source icon.
 */
public class MemberMapIcons {
    private static final BufferedImage BASE = ImageUtil.loadImageResource(MemberMapIcons.class, "player-icon.png");

    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    /** @param heightPx target rendered height; width is scaled to match the source aspect ratio. */
    public BufferedImage get(String hexColor, int heightPx) {
        String key = hexColor + "@" + heightPx;
        return cache.computeIfAbsent(key, k -> render(hexColor, heightPx));
    }

    private static BufferedImage render(String hexColor, int heightPx) {
        Color target;
        try {
            target = Color.decode(hexColor);
        } catch (Exception e) {
            target = Color.WHITE;
        }
        float[] targetHsb = Color.RGBtoHSB(target.getRed(), target.getGreen(), target.getBlue(), null);

        BufferedImage tinted = new BufferedImage(BASE.getWidth(), BASE.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < BASE.getHeight(); y++) {
            for (int x = 0; x < BASE.getWidth(); x++) {
                int argb = BASE.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                float brightness = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null)[2];
                int rgb = Color.HSBtoRGB(targetHsb[0], targetHsb[1], brightness);
                tinted.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
            }
        }

        int width = Math.max(1, Math.round(BASE.getWidth() * ((float) heightPx / BASE.getHeight())));
        Image scaled = tinted.getScaledInstance(width, heightPx, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(width, heightPx, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(scaled, 0, 0, null);
        return out;
    }
}
