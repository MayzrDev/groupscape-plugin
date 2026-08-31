package com.groupscape.sidepanel;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.FontManager;

/**
 * Attack/Defence/Other combat bonus totals, summed directly from each equipped item's
 * {@link ItemEquipmentStats} via {@link ItemManager#getItemStats(int)} - RuneLite's own bundled
 * item-stat cache - rather than the website's server-side item-bonuses dataset
 * (server/src/item_bonuses.rs), so this needs no extra network round trip and works from
 * whatever gear a member's snapshot equipment array reports equipped.
 *
 * <p>{@code getItemStats} falls back to {@code Client#getItemDefinition} on a cache miss, which
 * asserts it's running on the client thread - so the actual lookup+sum runs there via
 * {@link ClientThread#invoke}, with only the resulting label text handed back to the EDT.
 * Calling it straight from {@link #setEquipment} (as this used to) crashed the sidepanel's whole
 * refresh timer the first time an uncached item showed up in someone's gear.
 */
class EquipmentBonusesPanel extends JPanel {
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    private final JLabel attackStab = new JLabel("+0");
    private final JLabel attackSlash = new JLabel("+0");
    private final JLabel attackCrush = new JLabel("+0");
    private final JLabel attackMagic = new JLabel("+0");
    private final JLabel attackRanged = new JLabel("+0");
    private final JLabel defenceStab = new JLabel("+0");
    private final JLabel defenceSlash = new JLabel("+0");
    private final JLabel defenceCrush = new JLabel("+0");
    private final JLabel defenceMagic = new JLabel("+0");
    private final JLabel defenceRanged = new JLabel("+0");
    private final JLabel meleeStrength = new JLabel("+0");
    private final JLabel rangedStrength = new JLabel("+0");
    private final JLabel magicDamage = new JLabel("+0%");
    private final JLabel prayerBonus = new JLabel("+0");
    private final JLabel weaponSpeed = new JLabel("-");
    private final JLabel undeadBonus = new JLabel("+0%");
    private final JLabel slayerBonus = new JLabel("+0%");

    private List<Integer> lastRendered = null;

    EquipmentBonusesPanel(ItemManager itemManager, ClientThread clientThread) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        setBackground(SidePanelTheme.SLOT_BG);
        setBorder(new CompoundBorder(new LineBorder(SidePanelTheme.BORDER, 1), new EmptyBorder(6, 6, 6, 6)));

        add(heading("Attack bonus"));
        add(grid(
                "Stab", attackStab, "Slash", attackSlash, "Crush", attackCrush,
                "Magic", attackMagic, "Ranged", attackRanged
        ));
        add(heading("Defence bonus"));
        add(grid(
                "Stab", defenceStab, "Slash", defenceSlash, "Crush", defenceCrush,
                "Magic", defenceMagic, "Ranged", defenceRanged
        ));
        add(heading("Other bonuses"));
        add(grid(
                "Melee str", meleeStrength, "Ranged str", rangedStrength,
                "Magic dmg", magicDamage, "Prayer", prayerBonus
        ));
        add(heading("Target-specific"));
        add(grid("Undead", undeadBonus, "Slayer", slayerBonus));
        add(heading("Weapon speed"));
        add(grid("Speed", weaponSpeed));
    }

    private JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(SidePanelTheme.ACCENT);
        label.setBorder(new EmptyBorder(6, 0, 2, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JPanel grid(Object... labelValuePairs) {
        JPanel panel = new JPanel(new GridLayout(labelValuePairs.length / 2, 2, 4, 1));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            JLabel key = new JLabel((String) labelValuePairs[i]);
            key.setFont(FontManager.getRunescapeSmallFont());
            key.setForeground(SidePanelTheme.MUTED);
            panel.add(key);

            JLabel value = (JLabel) labelValuePairs[i + 1];
            value.setFont(FontManager.getRunescapeSmallFont());
            value.setForeground(SidePanelTheme.TEXT);
            value.setHorizontalAlignment(SwingConstants.RIGHT);
            panel.add(value);
        }
        return panel;
    }

    void setEquipment(List<Integer> flatIdQuantityPairs) {
        List<Integer> items = flatIdQuantityPairs == null ? List.of() : flatIdQuantityPairs;
        if (items.equals(lastRendered)) return;
        lastRendered = new ArrayList<>(items);
        List<Integer> request = lastRendered;

        clientThread.invoke(() -> {
            Totals totals = computeTotals(request);
            SwingUtilities.invokeLater(() -> {
                if (request == lastRendered) {
                    applyTotals(totals);
                }
            });
        });
    }

    private Totals computeTotals(List<Integer> items) {
        Totals totals = new Totals();
        List<Integer> equippedItemIds = new ArrayList<>();

        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
            int idIndex = slot.getSlotIdx() * 2;
            int id = idIndex < items.size() ? items.get(idIndex) : 0;
            if (id <= 0) continue;
            equippedItemIds.add(id);

            ItemStats stats = itemManager.getItemStats(id);
            ItemEquipmentStats eq = stats != null ? stats.getEquipment() : null;
            if (eq == null) continue;

            totals.aStab += eq.getAstab();
            totals.aSlash += eq.getAslash();
            totals.aCrush += eq.getAcrush();
            totals.aMagic += eq.getAmagic();
            totals.aRanged += eq.getArange();
            totals.dStab += eq.getDstab();
            totals.dSlash += eq.getDslash();
            totals.dCrush += eq.getDcrush();
            totals.dMagic += eq.getDmagic();
            totals.dRanged += eq.getDrange();
            totals.str += eq.getStr();
            totals.rstr += eq.getRstr();
            totals.prayer += eq.getPrayer();
            totals.mdmg += eq.getMdmg();

            if (slot == EquipmentInventorySlot.WEAPON) {
                totals.speed = eq.getAspeed();
            }
        }

        totals.undeadPercent = TargetBonuses.undeadPercent(equippedItemIds);
        totals.slayerPercent = TargetBonuses.slayerPercent(equippedItemIds);
        return totals;
    }

    private void applyTotals(Totals totals) {
        attackStab.setText(signed(totals.aStab));
        attackSlash.setText(signed(totals.aSlash));
        attackCrush.setText(signed(totals.aCrush));
        attackMagic.setText(signed(totals.aMagic));
        attackRanged.setText(signed(totals.aRanged));
        defenceStab.setText(signed(totals.dStab));
        defenceSlash.setText(signed(totals.dSlash));
        defenceCrush.setText(signed(totals.dCrush));
        defenceMagic.setText(signed(totals.dMagic));
        defenceRanged.setText(signed(totals.dRanged));
        meleeStrength.setText(signed(totals.str));
        rangedStrength.setText(signed(totals.rstr));
        magicDamage.setText(signed((int) Math.round(totals.mdmg)) + "%");
        prayerBonus.setText(signed(totals.prayer));
        weaponSpeed.setText(totals.speed > 0 ? totals.speed + "t" : "-");
        undeadBonus.setText(formatPercent(totals.undeadPercent));
        slayerBonus.setText(formatPercent(totals.slayerPercent));
    }

    private static final class Totals {
        int aStab, aSlash, aCrush, aMagic, aRanged;
        int dStab, dSlash, dCrush, dMagic, dRanged;
        int str, rstr, prayer;
        double mdmg;
        int speed = -1;
        double undeadPercent;
        double slayerPercent;
    }

    private static String formatPercent(double percent) {
        return "+" + (percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent)) + "%";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
