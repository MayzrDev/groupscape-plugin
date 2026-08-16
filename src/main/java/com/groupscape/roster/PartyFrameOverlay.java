package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.RichPresenceState;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * In-game overlay showing every group member's HP/prayer/run/spec bars,
 * active prayers, and current activity, ported from groupscape-old's
 * PartyFrameOverlay. The local player's own row is read live from the
 * Client each frame (bypassing the roster) so it never shows round-trip lag;
 * every other member's row comes from {@link RosterState}, fed by
 * {@link RosterClient}'s WebSocket push.
 */
public class PartyFrameOverlay extends Overlay {
    private static final long OFFLINE_THRESHOLD_MS = 60_000;
    private static final int PANEL_WIDTH = 180;
    private static final int PADDING = 6;
    private static final int MEMBER_GAP = 6;
    private static final int LINE_HEIGHT = 13;
    private static final int BAR_HEIGHT = 11;
    private static final int BAR_GAP = 2;
    private static final int PRAYER_ICON_ROW_HEIGHT = 12;
    private static final float OFFLINE_ALPHA = 0.55f;

    private static final Color BG = new Color(40, 34, 24, 220);
    private static final Color BORDER = new Color(88, 70, 44);
    private static final Color TEXT = new Color(255, 245, 220);
    private static final Color MUTED_TEXT = new Color(190, 175, 150);
    private static final Color HP_COLOR = new Color(198, 63, 58);
    private static final Color PRAYER_COLOR = new Color(58, 139, 214);
    private static final Color RUN_COLOR = new Color(76, 175, 80);
    private static final Color SPEC_COLOR = new Color(232, 197, 71);
    private static final Color TRACK_COLOR = new Color(255, 255, 255, 40);
    private static final Color FIGHTING_COLOR = new Color(240, 128, 128);
    private static final Color TALKING_COLOR = new Color(120, 180, 240);
    private static final Color DEFAULT_ACTIVITY_COLOR = new Color(232, 197, 71);

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;

    public PartyFrameOverlay(Client client, GroupScapeTrackerConfig config, RosterState rosterState) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getName() == null) {
            return null;
        }

        List<RosterMember> members = buildMemberList(localPlayer.getName());
        if (members.isEmpty()) {
            return null;
        }

        sortMembers(members);

        graphics.setFont(FontManager.getRunescapeSmallFont());

        int height = PADDING;
        for (RosterMember member : members) {
            height += memberHeight(member);
        }
        height += PADDING;

        drawChrome(graphics, PANEL_WIDTH, height);

        int y = PADDING;
        for (RosterMember member : members) {
            boolean self = member.name.equalsIgnoreCase(localPlayer.getName());
            boolean offline = !self && isOffline(member);
            y = drawMember(graphics, member, y, offline);
        }

        return new Dimension(PANEL_WIDTH, height);
    }

    private List<RosterMember> buildMemberList(String localPlayerName) {
        List<RosterMember> members = new ArrayList<>();

        if (!config.partyOverlayHideSelf()) {
            members.add(buildSelfRow(localPlayerName));
        }

        for (RosterMember member : rosterState.all()) {
            if (member.name.equalsIgnoreCase(localPlayerName)) {
                continue;
            }
            if (config.partyOverlayHideOfflineMembers() && isOffline(member)) {
                continue;
            }
            members.add(member);
        }

        return members;
    }

    /** Reads the local player's own vitals straight from Client, avoiding any WebSocket round-trip. */
    private RosterMember buildSelfRow(String name) {
        RosterMember self = new RosterMember(name);
        self.color = "#FFD700";
        self.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        self.maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
        self.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
        self.maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        self.runEnergy = client.getEnergy();
        self.specEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
        self.world = client.getWorld();
        self.lastHeartbeatAt = Instant.now();
        self.richPresence = RichPresenceState.computeText(client);
        self.activePrayers = new ActivePrayersStateReader(client).activePrayerNames();
        return self;
    }

    private void sortMembers(List<RosterMember> members) {
        Comparator<RosterMember> comparator;
        switch (config.partyOverlaySortOrder()) {
            case ALPHABETICAL:
                comparator = Comparator.comparing(m -> m.name.toLowerCase());
                break;
            case LOWEST_HP_FIRST:
                comparator = Comparator.comparingInt(this::hpRatioForSort);
                break;
            case JOIN_ORDER:
            default:
                return; // roster order already reflects join order
        }

        if (config.partyOverlayOfflineMembersLast()) {
            comparator = Comparator.comparing((RosterMember m) -> isOffline(m) ? 1 : 0).thenComparing(comparator);
        }
        members.sort(comparator);
    }

    private int hpRatioForSort(RosterMember member) {
        if (member.hp == null || member.maxHp == null || member.maxHp == 0) {
            return Integer.MAX_VALUE;
        }
        return (member.hp * 100) / member.maxHp;
    }

    private boolean isOffline(RosterMember member) {
        if (member.lastHeartbeatAt == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= OFFLINE_THRESHOLD_MS;
    }

    private int memberHeight(RosterMember member) {
        int height = LINE_HEIGHT; // name row
        if (!config.partyOverlayHideHp()) height += BAR_HEIGHT + BAR_GAP;
        if (!config.partyOverlayHidePrayer()) {
            height += BAR_HEIGHT + BAR_GAP;
            if (member.activePrayers != null && !member.activePrayers.isEmpty()) {
                height += PRAYER_ICON_ROW_HEIGHT;
            }
        }
        if (!config.partyOverlayHideRun()) height += BAR_HEIGHT + BAR_GAP;
        if (!config.partyOverlayHideSpec()) height += BAR_HEIGHT + BAR_GAP;
        if (!config.partyOverlayHideTarget() && member.richPresence != null && !member.richPresence.isEmpty()) {
            height += LINE_HEIGHT;
        }
        return height + MEMBER_GAP;
    }

    private int drawMember(Graphics2D graphics, RosterMember member, int y, boolean offline) {
        int startY = y;
        Color stripeColor = memberColor(member.color);

        float alpha = offline ? OFFLINE_ALPHA : 1f;
        java.awt.Composite originalComposite = graphics.getComposite();
        if (offline) {
            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        graphics.setColor(stripeColor);
        graphics.fillRect(PADDING, y, 2, memberHeight(member) - MEMBER_GAP);

        int textX = PADDING + 6;
        int barWidth = PANEL_WIDTH - textX - PADDING;

        graphics.setColor(TEXT);
        String nameLine = member.name + (!config.partyOverlayHideWorld() && member.world != null ? "  W" + member.world : "");
        graphics.drawString(nameLine, textX, y + 10);
        y += LINE_HEIGHT;

        if (!config.partyOverlayHideHp()) {
            drawBar(graphics, textX, y, barWidth, "HP", member.hp, member.maxHp, HP_COLOR);
            y += BAR_HEIGHT + BAR_GAP;
        }

        if (!config.partyOverlayHidePrayer()) {
            drawBar(graphics, textX, y, barWidth, "Pr", member.prayer, member.maxPrayer, PRAYER_COLOR);
            y += BAR_HEIGHT + BAR_GAP;

            if (member.activePrayers != null && !member.activePrayers.isEmpty()) {
                drawPrayerIcons(graphics, textX, y, member.activePrayers);
                y += PRAYER_ICON_ROW_HEIGHT;
            }
        }

        if (!config.partyOverlayHideRun()) {
            drawBar(graphics, textX, y, barWidth, "Run", member.runEnergy, 100, RUN_COLOR);
            y += BAR_HEIGHT + BAR_GAP;
        }

        if (!config.partyOverlayHideSpec()) {
            drawBar(graphics, textX, y, barWidth, "Sp", member.specEnergy, 100, SPEC_COLOR);
            y += BAR_HEIGHT + BAR_GAP;
        }

        if (!config.partyOverlayHideTarget() && member.richPresence != null && !member.richPresence.isEmpty()) {
            graphics.setColor(activityColor(member.richPresence));
            graphics.drawString(member.richPresence, textX, y + 10);
            y += LINE_HEIGHT;
        }

        if (offline) {
            graphics.setComposite(originalComposite);
        }

        return startY + memberHeight(member);
    }

    private void drawBar(Graphics2D graphics, int x, int y, int width, String label, Integer value, Integer max, Color color) {
        graphics.setColor(MUTED_TEXT);
        graphics.drawString(label, x, y + BAR_HEIGHT - 2);

        int labelWidth = 22;
        int barX = x + labelWidth;
        int barWidth = width - labelWidth - 26;

        graphics.setColor(TRACK_COLOR);
        graphics.fillRect(barX, y, barWidth, BAR_HEIGHT);

        if (value != null && max != null && max > 0) {
            int clampedValue = Math.max(0, Math.min(value, max));
            int filledWidth = (int) ((clampedValue / (double) max) * barWidth);
            graphics.setColor(color);
            graphics.fillRect(barX, y, filledWidth, BAR_HEIGHT);

            graphics.setColor(TEXT);
            graphics.drawString(String.valueOf(clampedValue), barX + barWidth + 4, y + BAR_HEIGHT - 2);
        } else {
            graphics.setColor(MUTED_TEXT);
            graphics.drawString("--", barX + barWidth + 4, y + BAR_HEIGHT - 2);
        }
    }

    private void drawPrayerIcons(Graphics2D graphics, int x, int y, List<String> activePrayers) {
        int iconSize = 10;
        int gap = 2;
        int drawX = x;
        graphics.setColor(PRAYER_COLOR);
        for (String prayerName : activePrayers) {
            graphics.fillRoundRect(drawX, y, iconSize, iconSize, 3, 3);
            graphics.setColor(new Color(20, 20, 20));
            graphics.drawString(abbreviate(prayerName), drawX + 1, y + iconSize - 1);
            graphics.setColor(PRAYER_COLOR);
            drawX += iconSize + gap;
            if (drawX + iconSize > x + (PANEL_WIDTH - PADDING * 3)) {
                break;
            }
        }
    }

    private static String abbreviate(String prayerName) {
        String[] parts = prayerName.split("_");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }

    private Color activityColor(String richPresence) {
        if (richPresence.startsWith("Fighting")) return FIGHTING_COLOR;
        if (richPresence.startsWith("Talking")) return TALKING_COLOR;
        return DEFAULT_ACTIVITY_COLOR;
    }

    private static Color memberColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return BORDER;
        }
    }

    private void drawChrome(Graphics2D graphics, int width, int height) {
        graphics.setColor(BG);
        graphics.fillRoundRect(0, 0, width, height, 6, 6);
        graphics.setColor(BORDER);
        graphics.drawRoundRect(0, 0, width - 1, height - 1, 6, 6);
    }

    /** Small local helper so the self-row can reuse the same active-prayer scan as {@code ActivePrayersState}. */
    private static class ActivePrayersStateReader {
        private final Client client;

        ActivePrayersStateReader(Client client) {
            this.client = client;
        }

        List<String> activePrayerNames() {
            List<String> names = new ArrayList<>();
            for (net.runelite.api.Prayer prayer : net.runelite.api.Prayer.values()) {
                if (client.isPrayerActive(prayer)) {
                    names.add(prayer.name());
                }
            }
            return names;
        }
    }
}
