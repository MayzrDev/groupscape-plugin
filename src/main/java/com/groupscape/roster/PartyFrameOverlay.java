package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.NpcDialogueTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
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
    private static final int PANEL_WIDTH = 180;
    private static final float OFFLINE_ALPHA = 0.55f;

    private static final int NORMAL_PADDING = 6;
    private static final int NORMAL_MEMBER_GAP = 6;
    private static final int NORMAL_LINE_HEIGHT = 13;
    private static final int NORMAL_BAR_HEIGHT = 11;
    private static final int NORMAL_BAR_GAP = 2;
    private static final int NORMAL_PRAYER_ICON_ROW_HEIGHT = 12;

    private static final int COMPACT_PADDING = 4;
    private static final int COMPACT_MEMBER_GAP = 3;
    private static final int COMPACT_LINE_HEIGHT = 11;
    private static final int COMPACT_BAR_HEIGHT = 8;
    private static final int COMPACT_BAR_GAP = 1;
    private static final int COMPACT_PRAYER_ICON_ROW_HEIGHT = 10;

    private int padding = NORMAL_PADDING;
    private int memberGap = NORMAL_MEMBER_GAP;
    private int lineHeight = NORMAL_LINE_HEIGHT;
    private int barHeight = NORMAL_BAR_HEIGHT;
    private int barGap = NORMAL_BAR_GAP;
    private int prayerIconRowHeight = NORMAL_PRAYER_ICON_ROW_HEIGHT;

    private static final Color BG_BASE = new Color(40, 34, 24);
    private static final Color BORDER = new Color(88, 70, 44);
    private static final Color TEXT = new Color(255, 245, 220);
    private static final Color MUTED_TEXT = new Color(190, 175, 150);
    private static final Color HP_COLOR = new Color(198, 63, 58);
    private static final Color PRAYER_COLOR = new Color(58, 139, 214);
    private static final Color RUN_COLOR = new Color(76, 175, 80);
    private static final Color SPEC_COLOR = new Color(232, 197, 71);
    private static final Color TRACK_COLOR = new Color(255, 255, 255, 40);

    // Target bar — mirrors the webapp's player-interacting component (red for an actual
    // combat target, gold for a neutral interaction like banking or talking to an NPC).
    private static final Color TARGET_COMBAT_FILL = new Color(164, 22, 35);
    private static final Color TARGET_COMBAT_TRACK = new Color(164, 22, 35, 41);
    private static final Color TARGET_COMBAT_BORDER = new Color(164, 22, 35, 128);
    private static final Color TARGET_COMBAT_LABEL = new Color(217, 138, 138);
    private static final Color TARGET_NEUTRAL_FILL = new Color(140, 98, 18);
    private static final Color TARGET_NEUTRAL_TRACK = new Color(140, 98, 18, 41);
    private static final Color TARGET_NEUTRAL_BORDER = new Color(140, 98, 18, 128);
    private static final Color TARGET_NEUTRAL_LABEL = new Color(217, 184, 119);

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;
    private final NpcDialogueTracker dialogueTracker;

    /** Row bounds from the last render, in overlay-local coordinates, for right-click hit-testing. */
    private final Map<Rectangle, RosterMember> lastRenderedRows = new LinkedHashMap<>();

    public PartyFrameOverlay(Client client, GroupScapeTrackerConfig config, RosterState rosterState, NpcDialogueTracker dialogueTracker) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        this.dialogueTracker = dialogueTracker;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.partyOverlayHideOverlay()) {
            return null;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getName() == null) {
            return null;
        }

        List<RosterMember> members = buildMemberList(localPlayer.getName());
        if (members.isEmpty()) {
            return null;
        }

        sortMembers(members);
        applyScale();

        int extraCount = 0;
        int maxMembers = config.partyOverlayMaxMembers();
        if (maxMembers > 0 && members.size() > maxMembers) {
            extraCount = members.size() - maxMembers;
            members = members.subList(0, maxMembers);
        }

        graphics.setFont(FontManager.getRunescapeSmallFont());

        int height = padding;
        for (RosterMember member : members) {
            height += memberHeight(member);
        }
        if (extraCount > 0) {
            height += lineHeight;
        }
        height += padding;

        drawChrome(graphics, PANEL_WIDTH, height);

        lastRenderedRows.clear();
        int y = padding;
        for (RosterMember member : members) {
            boolean self = member.name.equalsIgnoreCase(localPlayer.getName());
            boolean offline = !self && isOffline(member);
            int startY = y;
            y = drawMember(graphics, member, y, offline);
            lastRenderedRows.put(new Rectangle(0, startY, PANEL_WIDTH, y - startY), member);
        }

        if (extraCount > 0) {
            graphics.setColor(MUTED_TEXT);
            graphics.drawString("+" + extraCount + " more", padding + 6, y + 10);
        }

        return new Dimension(PANEL_WIDTH, height);
    }

    /**
     * Returns the roster member whose row was drawn under the given canvas position on the last
     * render, or null if the position isn't over this overlay. {@code canvasX}/{@code canvasY}
     * are in the same coordinate space as {@link Client#getMouseCanvasPosition()}; they're
     * translated into overlay-local space using {@link #getBounds()}, which RuneLite's
     * OverlayRenderer keeps in sync with where this overlay was last actually drawn.
     */
    public RosterMember memberAt(int canvasX, int canvasY) {
        Rectangle bounds = getBounds();
        if (bounds == null || bounds.isEmpty()) {
            return null;
        }
        int localX = canvasX - bounds.x;
        int localY = canvasY - bounds.y;
        for (Map.Entry<Rectangle, RosterMember> entry : lastRenderedRows.entrySet()) {
            if (entry.getKey().contains(localX, localY)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void applyScale() {
        boolean compact = config.partyOverlayScale() == GroupScapeTrackerConfig.PartyOverlayScale.COMPACT;
        padding = compact ? COMPACT_PADDING : NORMAL_PADDING;
        memberGap = compact ? COMPACT_MEMBER_GAP : NORMAL_MEMBER_GAP;
        lineHeight = compact ? COMPACT_LINE_HEIGHT : NORMAL_LINE_HEIGHT;
        barHeight = compact ? COMPACT_BAR_HEIGHT : NORMAL_BAR_HEIGHT;
        barGap = compact ? COMPACT_BAR_GAP : NORMAL_BAR_GAP;
        prayerIconRowHeight = compact ? COMPACT_PRAYER_ICON_ROW_HEIGHT : NORMAL_PRAYER_ICON_ROW_HEIGHT;
    }

    private Color bgColor() {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, config.partyOverlayOpacity())) * 2.55);
        return new Color(BG_BASE.getRed(), BG_BASE.getGreen(), BG_BASE.getBlue(), alpha);
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
        self.runEnergy = client.getEnergy() / 100;
        self.specEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
        self.world = client.getWorld();
        self.lastHeartbeatAt = Instant.now();
        self.activePrayers = new ActivePrayersStateReader(client).activePrayerNames();
        applyLocalTarget(self);
        return self;
    }

    /**
     * Fills the self row's target fields straight from Client, the same signals
     * {@link com.groupscape.RichPresenceState} used to build its text ("Fighting X" / "Talking to
     * X" / "Browsing the bank"), but kept as structured name+ratio+scale so the bar can render an
     * actual HP fill instead of a static line of text.
     */
    private void applyLocalTarget(RosterMember self) {
        Player player = client.getLocalPlayer();

        Actor interacting = player.getInteracting();
        if (interacting != null && interacting.getName() != null) {
            self.targetName = interacting.getName();
            self.targetHealthScale = interacting.getHealthScale();
            self.targetHealthRatio = interacting.getHealthRatio();
            return;
        }

        // getInteracting() has already gone null once the dialogue box is actually open (see
        // NpcDialogueTracker) but the box is still up, so the player is still "talking to"
        // whoever they last targeted.
        if (dialogueTracker != null && dialogueTracker.lastNpcName() != null) {
            self.targetName = dialogueTracker.lastNpcName();
            self.targetHealthScale = 0;
            self.targetHealthRatio = -1;
            return;
        }

        if (client.getWidget(InterfaceID.Bankmain.ITEMS) != null) {
            self.targetName = "Bank";
            self.targetHealthScale = 0;
            self.targetHealthRatio = -1;
            return;
        }

        self.targetName = null;
        self.targetHealthScale = null;
        self.targetHealthRatio = null;
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
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= RosterMember.OFFLINE_THRESHOLD_MS;
    }

    private int memberHeight(RosterMember member) {
        int height = lineHeight; // name row
        if (!config.partyOverlayHideHp()) height += barHeight + barGap;
        if (!config.partyOverlayHidePrayer()) {
            height += barHeight + barGap;
            if (member.activePrayers != null && !member.activePrayers.isEmpty()) {
                height += prayerIconRowHeight;
            }
        }
        if (!config.partyOverlayHideRun()) height += barHeight + barGap;
        if (!config.partyOverlayHideSpec()) height += barHeight + barGap;
        if (!config.partyOverlayHideTarget() && member.targetName != null && !member.targetName.isEmpty()) {
            height += barHeight + barGap;
        }
        return height + memberGap;
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
        graphics.fillRect(padding, y, 2, memberHeight(member) - memberGap);

        int textX = padding + 6;
        int barWidth = PANEL_WIDTH - textX - padding;

        graphics.setColor(TEXT);
        String nameLine = member.name + (!config.partyOverlayHideWorld() && member.world != null ? "  W" + member.world : "");
        graphics.drawString(nameLine, textX, y + 10);
        y += lineHeight;

        if (!config.partyOverlayHideHp()) {
            drawBar(graphics, textX, y, barWidth, "HP", member.hp, member.maxHp, HP_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHidePrayer()) {
            drawBar(graphics, textX, y, barWidth, "Pr", member.prayer, member.maxPrayer, PRAYER_COLOR);
            y += barHeight + barGap;

            if (member.activePrayers != null && !member.activePrayers.isEmpty()) {
                drawPrayerIcons(graphics, textX, y, member.activePrayers);
                y += prayerIconRowHeight;
            }
        }

        if (!config.partyOverlayHideRun()) {
            drawBar(graphics, textX, y, barWidth, "Run", member.runEnergy, 100, RUN_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHideSpec()) {
            drawBar(graphics, textX, y, barWidth, "Sp", member.specEnergy, 100, SPEC_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHideTarget() && member.targetName != null && !member.targetName.isEmpty()) {
            drawTargetBar(graphics, textX, y, barWidth, member);
            y += barHeight + barGap;
        }

        if (offline) {
            graphics.setComposite(originalComposite);
        }

        return startY + memberHeight(member);
    }

    private void drawBar(Graphics2D graphics, int x, int y, int width, String label, Integer value, Integer max, Color color) {
        graphics.setColor(MUTED_TEXT);
        graphics.drawString(label, x, y + barHeight - 2);

        int labelWidth = 22;
        int barX = x + labelWidth;
        int barWidth = width - labelWidth - 26;

        graphics.setColor(TRACK_COLOR);
        graphics.fillRect(barX, y, barWidth, barHeight);

        if (value != null && max != null && max > 0) {
            int clampedValue = Math.max(0, Math.min(value, max));
            int filledWidth = (int) ((clampedValue / (double) max) * barWidth);
            graphics.setColor(color);
            graphics.fillRect(barX, y, filledWidth, barHeight);

            graphics.setColor(TEXT);
            graphics.drawString(String.valueOf(clampedValue), barX + barWidth + 4, y + barHeight - 2);
        } else {
            graphics.setColor(MUTED_TEXT);
            graphics.drawString("--", barX + barWidth + 4, y + barHeight - 2);
        }
    }

    /**
     * Renders the "target" row as an HP-style bar instead of plain text, laid out exactly like
     * {@link #drawBar} (label outside to the left, value outside to the right) so it lines up
     * with the HP/Pr/Run/Sp rows above it. Styled to match the webapp's player-interacting
     * component: red bar filled by HP ratio for an actual combat target (health scale &gt; 0),
     * full gold bar with no HP value for a neutral interaction (banking, talking to an NPC). The
     * target's name renders inside the bar and truncates with an ellipsis instead of overflowing.
     */
    private void drawTargetBar(Graphics2D graphics, int x, int y, int width, RosterMember member) {
        boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
        boolean hasRatio = isEnemy && member.targetHealthRatio != null && member.targetHealthRatio >= 0;

        Color fill = isEnemy ? TARGET_COMBAT_FILL : TARGET_NEUTRAL_FILL;
        Color track = isEnemy ? TARGET_COMBAT_TRACK : TARGET_NEUTRAL_TRACK;
        Color border = isEnemy ? TARGET_COMBAT_BORDER : TARGET_NEUTRAL_BORDER;
        Color labelColor = isEnemy ? TARGET_COMBAT_LABEL : TARGET_NEUTRAL_LABEL;

        graphics.setColor(labelColor);
        graphics.drawString("Tgt", x, y + barHeight - 2);

        int labelWidth = 22;
        int barX = x + labelWidth;
        int barWidth = width - labelWidth - 26;

        graphics.setColor(track);
        graphics.fillRect(barX, y, barWidth, barHeight);

        int filledWidth = barWidth;
        if (hasRatio) {
            double ratio = Math.max(0, Math.min(1.0, member.targetHealthRatio / (double) member.targetHealthScale));
            filledWidth = (int) (ratio * barWidth);
        }
        graphics.setColor(fill);
        graphics.fillRect(barX, y, filledWidth, barHeight);

        graphics.setColor(border);
        graphics.drawRect(barX, y, barWidth - 1, barHeight - 1);

        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(TEXT);
        String name = truncateToWidth(metrics, member.targetName, Math.max(0, barWidth - 6));
        graphics.drawString(name, barX + 3, y + barHeight - 2);

        if (hasRatio) {
            graphics.setColor(labelColor);
            String hpText = member.targetHealthRatio + "/" + member.targetHealthScale;
            graphics.drawString(hpText, barX + barWidth + 4, y + barHeight - 2);
        }
    }

    private static String truncateToWidth(FontMetrics metrics, String text, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return "";
        }

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
            if (drawX + iconSize > x + (PANEL_WIDTH - padding * 3)) {
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

    private static Color memberColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return BORDER;
        }
    }

    private void drawChrome(Graphics2D graphics, int width, int height) {
        graphics.setColor(bgColor());
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
