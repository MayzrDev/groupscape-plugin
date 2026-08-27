package com.groupscape.sidepanel;

import com.groupscape.roster.RosterMember;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * Paints HP/Prayer/Run/Spec bars plus the target bar for one roster member, ported from
 * {@code PartyFrameOverlay#drawBar}/{@code drawTargetBar} but full-width for the sidepanel's
 * ~220px column instead of the overlay's fixed 180px canvas.
 */
class VitalsBarsPanel extends JComponent {
    private static final int ROW_GAP = 2;
    private static final int LABEL_WIDTH = 22;
    private static final int VALUE_WIDTH = 28;

    /**
     * Sized from the font's real metrics rather than a guessed constant - a hardcoded row height
     * shorter than the font's ascent+descent clipped the top pixels off every digit.
     */
    private final int rowHeight;

    private RosterMember member;
    private boolean showHp = true;
    private boolean showPrayer = true;
    private boolean showRun = true;
    private boolean showSpec = true;
    private boolean showTarget = true;

    VitalsBarsPanel() {
        setOpaque(false);
        setFont(FontManager.getRunescapeSmallFont());
        rowHeight = getFontMetrics(getFont()).getHeight() + 2;
    }

    void setMember(RosterMember member) {
        this.member = member;
        repaint();
    }

    /** Config-driven per-bar visibility - hidden bars neither render nor take up row height. */
    void setVisibleBars(boolean hp, boolean prayer, boolean run, boolean spec, boolean target) {
        this.showHp = hp;
        this.showPrayer = prayer;
        this.showRun = run;
        this.showSpec = spec;
        this.showTarget = target;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = visibleRowCount();
        int height = rows == 0 ? 0 : rows * rowHeight + (rows - 1) * ROW_GAP;
        return new Dimension(200, height);
    }

    private int visibleRowCount() {
        int rows = 0;
        if (showHp) rows++;
        if (showPrayer) rows++;
        if (showRun) rows++;
        if (showSpec) rows++;
        if (showTarget) rows++;
        return rows;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont());

        int width = getWidth();
        int y = 0;

        if (member == null) {
            g2.dispose();
            return;
        }

        if (showHp) y = drawBar(g2, y, width, "HP", member.hp, member.maxHp, SidePanelTheme.HP);
        if (showPrayer) y = drawBar(g2, y, width, "Pr", member.prayer, member.maxPrayer, SidePanelTheme.PRAYER);
        if (showRun) y = drawBar(g2, y, width, "Run", member.runEnergy, 100, SidePanelTheme.RUN);
        if (showSpec) y = drawBar(g2, y, width, "Sp", member.specEnergy, 100, SidePanelTheme.SPEC);
        if (showTarget) drawTargetBar(g2, y, width);

        g2.dispose();
    }

    private int drawBar(Graphics2D g2, int y, int width, String label, Integer value, Integer max, Color color) {
        FontMetrics metrics = g2.getFontMetrics();
        int textY = y + (rowHeight + metrics.getAscent()) / 2 - 1;

        g2.setColor(SidePanelTheme.MUTED);
        g2.drawString(label, 0, textY);

        int barX = LABEL_WIDTH;
        int barWidth = Math.max(0, width - LABEL_WIDTH - VALUE_WIDTH);

        g2.setColor(SidePanelTheme.TRACK);
        g2.fillRoundRect(barX, y + 1, barWidth, rowHeight - 2, 2, 2);

        String valueText;
        if (value != null && max != null && max > 0) {
            int clamped = Math.max(0, Math.min(value, max));
            int filled = (int) ((clamped / (double) max) * barWidth);
            g2.setColor(color);
            g2.fillRoundRect(barX, y + 1, filled, rowHeight - 2, 2, 2);
            valueText = String.valueOf(clamped);
        } else {
            valueText = "--";
        }

        g2.setColor(SidePanelTheme.TEXT);
        g2.drawString(valueText, barX + barWidth + 4, textY);

        return y + rowHeight + ROW_GAP;
    }

    private void drawTargetBar(Graphics2D g2, int y, int width) {
        FontMetrics metrics = g2.getFontMetrics();
        int textY = y + (rowHeight + metrics.getAscent()) / 2 - 1;

        String targetName = member.targetName;
        boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
        boolean hasRatio = isEnemy && member.targetHealthRatio != null && member.targetHealthRatio >= 0;

        Color fill = isEnemy ? SidePanelTheme.TARGET_COMBAT_FILL : SidePanelTheme.TARGET_NEUTRAL_FILL;
        Color track = isEnemy ? SidePanelTheme.TARGET_COMBAT_TRACK : SidePanelTheme.TARGET_NEUTRAL_TRACK;

        g2.setColor(SidePanelTheme.MUTED);
        g2.drawString("Tgt", 0, textY);

        int barX = LABEL_WIDTH;
        int barWidth = Math.max(0, width - LABEL_WIDTH - VALUE_WIDTH);

        if (targetName == null || targetName.isEmpty()) {
            g2.setColor(SidePanelTheme.TRACK);
            g2.fillRoundRect(barX, y + 1, barWidth, rowHeight - 2, 2, 2);
            g2.setColor(SidePanelTheme.MUTED);
            g2.drawString("--", barX + barWidth + 4, textY);
            return;
        }

        g2.setColor(track);
        g2.fillRoundRect(barX, y + 1, barWidth, rowHeight - 2, 2, 2);

        double ratio = hasRatio ? Math.max(0, Math.min(1.0, member.targetHealthRatio / (double) member.targetHealthScale)) : 1.0;
        int filled = (int) Math.round(barWidth * ratio);
        g2.setColor(fill);
        g2.fillRoundRect(barX, y + 1, filled, rowHeight - 2, 2, 2);

        g2.setColor(SidePanelTheme.TEXT);
        String name = truncateToWidth(metrics, targetName, Math.max(0, barWidth - 6));
        g2.drawString(name, barX + 3, textY);

        if (hasRatio) {
            g2.setColor(SidePanelTheme.MUTED);
            g2.drawString(Math.round(ratio * 100) + "%", barX + barWidth + 4, textY);
        }
    }

    private static String truncateToWidth(FontMetrics metrics, String text, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (maxWidth <= ellipsisWidth) return "";

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            truncated.append(text.charAt(i));
            if (metrics.stringWidth(truncated.toString()) + ellipsisWidth > maxWidth) {
                truncated.setLength(truncated.length() - 1);
                break;
            }
        }
        return truncated + ellipsis;
    }
}
