package com.groupscape.sidepanel;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Prayer;
import net.runelite.client.game.SpriteManager;

/** Row of real prayer-tab sprites for a member's active prayers, overhead/protection prayers first. */
class ActivePrayerIconsPanel extends JPanel {
    private static final int ICON_SIZE = 16;

    private final SpriteManager spriteManager;
    private List<String> lastRendered = null;

    ActivePrayerIconsPanel(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    void setActivePrayers(List<String> activePrayers) {
        List<String> prayers = activePrayers == null ? List.of() : activePrayers;
        if (prayers.equals(lastRendered)) return;
        lastRendered = new ArrayList<>(prayers);

        removeAll();
        setVisible(!prayers.isEmpty());
        if (prayers.isEmpty()) {
            revalidate();
            repaint();
            return;
        }

        List<String> ordered = new ArrayList<>(prayers);
        ordered.sort(Comparator.comparingInt(name -> isOverhead(name) ? 0 : 1));

        for (String name : ordered) {
            PrayerIcon icon = new PrayerIcon();
            Prayer prayer = PrayerSprites.parse(name);
            Integer spriteId = prayer != null ? PrayerSprites.SPRITE_IDS.get(prayer) : null;
            if (spriteId != null) {
                spriteManager.getSpriteAsync(spriteId, 0, icon::setSprite);
            }
            add(icon);
        }

        revalidate();
        repaint();
    }

    private static boolean isOverhead(String name) {
        Prayer prayer = PrayerSprites.parse(name);
        return prayer != null && PrayerSprites.OVERHEAD.contains(prayer);
    }

    /**
     * A plain {@code JLabel} icon here drew each prayer sprite at its native game resolution
     * (much larger than our 16px row), overflowing/clipping instead of shrinking to fit. Painting
     * it ourselves, scaled uniformly and centered - the same approach the in-game overlay already
     * uses for these same sprites - keeps every icon a clean 16x16 regardless of its source size.
     */
    private static final class PrayerIcon extends JComponent {
        private BufferedImage sprite;

        PrayerIcon() {
            setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            setOpaque(false);
        }

        void setSprite(BufferedImage sprite) {
            this.sprite = sprite;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0) {
                return;
            }
            double scale = Math.min(getWidth() / (double) sprite.getWidth(), getHeight() / (double) sprite.getHeight());
            int drawWidth = (int) Math.round(sprite.getWidth() * scale);
            int drawHeight = (int) Math.round(sprite.getHeight() * scale);
            int x = (getWidth() - drawWidth) / 2;
            int y = (getHeight() - drawHeight) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(sprite, x, y, drawWidth, drawHeight, this);
            g2.dispose();
        }
    }
}
