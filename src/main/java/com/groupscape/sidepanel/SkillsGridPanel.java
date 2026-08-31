package com.groupscape.sidepanel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;

/** 3-column grid of skill level boxes plus a total-level row, matching site/src/player-skills. */
class SkillsGridPanel extends JPanel {
    private static final int ICON_SIZE = 14;

    private final SkillIconManager skillIconManager;
    private final JLabel[] levelLabels = new JLabel[SkillOrder.GRID_ORDER.length];
    private final JLabel totalLevelValue;

    SkillsGridPanel(SkillIconManager skillIconManager) {
        this.skillIconManager = skillIconManager;
        setLayout(new BorderLayout(0, 4));
        setOpaque(false);

        JPanel grid = new JPanel(new GridLayout(SkillOrder.GRID_ORDER.length / 3, 3, 1, 1));
        grid.setBackground(SidePanelTheme.SLOT_BG);
        grid.setBorder(new LineBorder(SidePanelTheme.BORDER, 1));

        for (int i = 0; i < SkillOrder.GRID_ORDER.length; i++) {
            Skill skill = SkillOrder.GRID_ORDER[i];
            JPanel box = new JPanel(new BorderLayout(4, 0));
            box.setOpaque(false);
            box.setBorder(new EmptyBorder(1, 6, 1, 6));

            JLabel icon = new JLabel();
            java.awt.image.BufferedImage sprite = skillIconManager.getSkillImage(skill, true);
            if (sprite != null) {
                icon.setIcon(new ImageIcon(sprite.getScaledInstance(ICON_SIZE, ICON_SIZE, java.awt.Image.SCALE_SMOOTH)));
            }
            box.add(icon, BorderLayout.WEST);

            JLabel level = new JLabel("1", SwingConstants.RIGHT);
            level.setFont(FontManager.getRunescapeSmallFont());
            level.setForeground(SidePanelTheme.TEXT);
            levelLabels[i] = level;
            box.add(level, BorderLayout.CENTER);

            grid.add(box);
        }
        add(grid, BorderLayout.CENTER);

        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setBackground(SidePanelTheme.SLOT_BG);
        totalRow.setBorder(new CompoundBorder(new LineBorder(SidePanelTheme.BORDER, 1), new EmptyBorder(4, 6, 4, 6)));
        JLabel totalLevelLabel = new JLabel("Total level");
        totalLevelLabel.setFont(FontManager.getRunescapeSmallFont());
        totalLevelLabel.setForeground(SidePanelTheme.MUTED);
        totalLevelValue = new JLabel("0");
        totalLevelValue.setFont(FontManager.getRunescapeBoldFont());
        totalLevelValue.setForeground(SidePanelTheme.ACCENT);
        totalRow.add(totalLevelLabel, BorderLayout.WEST);
        totalRow.add(totalLevelValue, BorderLayout.EAST);
        add(totalRow, BorderLayout.SOUTH);
    }

    void setSkillXp(List<Integer> skillXpBySkillOrder) {
        List<Integer> xp = skillXpBySkillOrder == null ? List.of() : skillXpBySkillOrder;

        int totalLevel = 0;
        for (int i = 0; i < SkillOrder.GRID_ORDER.length; i++) {
            int skillIndex = indexOf(SkillOrder.GRID_ORDER[i]);
            int value = skillIndex >= 0 && skillIndex < xp.size() ? xp.get(skillIndex) : 0;
            int level = value > 0 ? Experience.getLevelForXp(value) : 1;
            levelLabels[i].setText(String.valueOf(level));
            totalLevel += level;
        }
        totalLevelValue.setText(String.format("%,d", totalLevel));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static int indexOf(Skill skill) {
        for (int i = 0; i < SkillOrder.SKILLS.length; i++) {
            if (SkillOrder.SKILLS[i] == skill) return i;
        }
        return -1;
    }
}
