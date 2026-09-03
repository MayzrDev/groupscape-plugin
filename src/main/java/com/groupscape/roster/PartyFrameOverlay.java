package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.NpcDialogueTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
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
    private static final int NORMAL_PRAYER_ICON_SIZE = 16;
    private static final int NORMAL_PRAYER_ICON_ROW_HEIGHT = NORMAL_PRAYER_ICON_SIZE + 1;

    private static final int COMPACT_PADDING = 4;
    private static final int COMPACT_MEMBER_GAP = 3;
    private static final int COMPACT_LINE_HEIGHT = 11;
    private static final int COMPACT_BAR_HEIGHT = 8;
    private static final int COMPACT_BAR_GAP = 1;
    private static final int COMPACT_PRAYER_ICON_SIZE = 13;
    private static final int COMPACT_PRAYER_ICON_ROW_HEIGHT = COMPACT_PRAYER_ICON_SIZE + 1;

    private static final int SUPER_COMPACT_PADDING = 3;
    private static final int SUPER_COMPACT_MEMBER_GAP = 2;
    private static final int SUPER_COMPACT_LINE_HEIGHT = 9;
    private static final int SUPER_COMPACT_BAR_HEIGHT = 7;
    private static final int SUPER_COMPACT_BAR_GAP = 1;
    private static final int SUPER_COMPACT_PRAYER_ICON_SIZE = 11;
    private static final int SUPER_COMPACT_PRAYER_ICON_ROW_HEIGHT = SUPER_COMPACT_PRAYER_ICON_SIZE + 1;

    // Minimal fuses HP/Prayer/Run/Spec into one segmented strip (see drawMinimalCluster) instead
    // of scaling down four separate labeled bars, so its member height drops far more than a
    // straight-line continuation of the Normal/Compact/Super Compact spacing progression would.
    private static final int MINIMAL_PADDING = 2;
    private static final int MINIMAL_MEMBER_GAP = 2;
    private static final int MINIMAL_LINE_HEIGHT = 9;
    private static final int MINIMAL_BAR_HEIGHT = 7;
    private static final int MINIMAL_BAR_GAP = 1;
    private static final int MINIMAL_PRAYER_ICON_SIZE = 10;
    private static final int MINIMAL_PRAYER_ICON_ROW_HEIGHT = MINIMAL_PRAYER_ICON_SIZE + 1;
    private static final int MINIMAL_TARGET_STRIP_HEIGHT = 4;
    private static final int MINIMAL_CLUSTER_SEGMENT_GAP = 2;

    // Orb Grid drops the row layout entirely: members tile as circular status orbs in a wrapping
    // grid instead of stacking, so it doesn't share the padding/lineHeight/barHeight fields above -
    // see renderOrbGrid/drawOrb. Unlike every other tier it doesn't render at the fixed
    // PANEL_WIDTH either: the panel is only as wide as it needs to be for up to ORB_MAX_COLS
    // orbs, and wraps into a new row past that, instead of always claiming the full row width.
    private static final int ORB_SIZE = 40;
    private static final int ORB_MAX_COLS = 5;
    private static final int ORB_FOOTER_HEIGHT = 16;
    private static final Color ORB_TARGET_ENEMY = new Color(164, 22, 35);
    private static final Color ORB_TARGET_NEUTRAL = new Color(140, 98, 18);
    private static final Color ORB_INITIALS_COLOR = new Color(36, 28, 15);
    private static final int ORB_PIP_RADIUS = 3;
    private static final int ORB_PIP_GAP = 5;
    // How far a pip's outer edge sits past the orb's own radius - the pips hang off the bottom of
    // the ring, not inside it, so padding/row-gap both need to clear this or they overflow the
    // panel/collide with the next row instead of just the orb's footprint.
    private static final int ORB_PIP_OVERHANG = ORB_PIP_GAP + ORB_PIP_RADIUS * 2;
    private static final int ORB_PADDING = ORB_PIP_OVERHANG + 1;
    private static final int ORB_GAP = ORB_PIP_OVERHANG + 1;

    // Scoreboard turns the panel sideways: members sit in narrow side-by-side columns instead of
    // stacked rows or a 2D grid, each a vertical HP meter with a tally-pip row and initials below
    // it - see renderScoreboard/drawScoreColumn. Like Orb Grid, panel size follows content instead
    // of the fixed PANEL_WIDTH.
    // Base dimensions scaled up 33% from the originally-approved mockup size.
    private static final int SCOREBOARD_PADDING = 11;
    private static final int SCOREBOARD_COL_WIDTH = 27;
    private static final int SCOREBOARD_GAP = 7;
    private static final int SCOREBOARD_BAR_WIDTH = 19;
    private static final int SCOREBOARD_BAR_HEIGHT = 69;
    private static final int SCOREBOARD_PIP_SIZE = 7;
    private static final int SCOREBOARD_PIP_H_GAP = 3;
    private static final int SCOREBOARD_PIP_ROW_GAP = 4;
    private static final int SCOREBOARD_LABEL_GAP = 13;
    private static final int SCOREBOARD_TARGET_HEADROOM = 8;
    private static final int SCOREBOARD_MAX_COLS = 8;
    // Label font: base 8f x1.33 (matches the geometry scale-up) x1.10 (extra legibility pass).
    private static final float SCOREBOARD_FONT_SIZE = 8f * 1.33f * 1.10f;

    private static final int PRAYER_ICON_GAP = 2;

    private int padding = NORMAL_PADDING;
    private int memberGap = NORMAL_MEMBER_GAP;
    private int lineHeight = NORMAL_LINE_HEIGHT;
    private int barHeight = NORMAL_BAR_HEIGHT;
    private int barGap = NORMAL_BAR_GAP;
    private int prayerIconSize = NORMAL_PRAYER_ICON_SIZE;
    private int prayerIconRowHeight = NORMAL_PRAYER_ICON_ROW_HEIGHT;
    private int targetStripHeight = NORMAL_BAR_HEIGHT;

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

    // Overhead/protection prayers get a gold accent behind their icon and sort to the front of
    // the active-prayer row, since they're the highest-priority thing to notice mid-fight.
    private static final Set<Prayer> OVERHEAD_PRAYERS = EnumSet.of(
            Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MAGIC,
            Prayer.RETRIBUTION, Prayer.REDEMPTION, Prayer.SMITE
    );
    private static final Color OVERHEAD_TINT = new Color(232, 197, 71, 130);

    // Activating one of these "upgraded" curses also flags its lower-tier prayers as active in
    // the client's prayer state (Deadeye lights up both Rigour and Eagle Eye too), so without
    // this the row would show all of them at once even though only Deadeye is actually selected.
    private static final Map<Prayer, Prayer> BASE_PRAYER_SUPPRESSED_BY_UPGRADE = new EnumMap<>(Prayer.class);
    static {
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.RIGOUR, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.EAGLE_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.HAWK_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.SHARP_EYE, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.AUGURY, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_MIGHT, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_LORE, Prayer.MYSTIC_VIGOUR);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.MYSTIC_WILL, Prayer.MYSTIC_VIGOUR);
    }

    private static final Map<Prayer, Integer> PRAYER_SPRITE_IDS = buildPrayerSpriteIds();

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final RosterState rosterState;
    private final NpcDialogueTracker dialogueTracker;
    private final SpriteManager spriteManager;

    /** Populated asynchronously from {@link #spriteManager} at construction; may be sparse for a few frames. */
    private final Map<Prayer, BufferedImage> prayerSprites = new EnumMap<>(Prayer.class);

    /** Row bounds from the last render, in overlay-local coordinates, for right-click hit-testing. */
    private final Map<Rectangle, RosterMember> lastRenderedRows = new LinkedHashMap<>();

    public PartyFrameOverlay(Client client, GroupScapeTrackerConfig config, RosterState rosterState, NpcDialogueTracker dialogueTracker, SpriteManager spriteManager) {
        this.client = client;
        this.config = config;
        this.rosterState = rosterState;
        this.dialogueTracker = dialogueTracker;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);

        for (Map.Entry<Prayer, Integer> entry : PRAYER_SPRITE_IDS.entrySet()) {
            Prayer prayer = entry.getKey();
            spriteManager.getSpriteAsync(entry.getValue(), 0, img -> prayerSprites.put(prayer, img));
        }
    }

    /**
     * Explicit mapping from {@link Prayer} to its {@link SpriteID} icon. Most names line up
     * (PROTECT_FROM_MELEE -&gt; PRAYER_PROTECT_FROM_MELEE) but at least one Ruinous Powers curse
     * doesn't (RP_UMBRA_VOW -&gt; PRAYER_RP_UMBRAS_VOW), so this is spelled out rather than
     * derived by string-concatenation/reflection.
     */
    private static Map<Prayer, Integer> buildPrayerSpriteIds() {
        Map<Prayer, Integer> ids = new EnumMap<>(Prayer.class);
        ids.put(Prayer.THICK_SKIN, SpriteID.PRAYER_THICK_SKIN);
        ids.put(Prayer.BURST_OF_STRENGTH, SpriteID.PRAYER_BURST_OF_STRENGTH);
        ids.put(Prayer.CLARITY_OF_THOUGHT, SpriteID.PRAYER_CLARITY_OF_THOUGHT);
        ids.put(Prayer.SHARP_EYE, SpriteID.PRAYER_SHARP_EYE);
        ids.put(Prayer.MYSTIC_WILL, SpriteID.PRAYER_MYSTIC_WILL);
        ids.put(Prayer.ROCK_SKIN, SpriteID.PRAYER_ROCK_SKIN);
        ids.put(Prayer.SUPERHUMAN_STRENGTH, SpriteID.PRAYER_SUPERHUMAN_STRENGTH);
        ids.put(Prayer.IMPROVED_REFLEXES, SpriteID.PRAYER_IMPROVED_REFLEXES);
        ids.put(Prayer.RAPID_RESTORE, SpriteID.PRAYER_RAPID_RESTORE);
        ids.put(Prayer.RAPID_HEAL, SpriteID.PRAYER_RAPID_HEAL);
        ids.put(Prayer.PROTECT_ITEM, SpriteID.PRAYER_PROTECT_ITEM);
        ids.put(Prayer.HAWK_EYE, SpriteID.PRAYER_HAWK_EYE);
        ids.put(Prayer.MYSTIC_LORE, SpriteID.PRAYER_MYSTIC_LORE);
        ids.put(Prayer.STEEL_SKIN, SpriteID.PRAYER_STEEL_SKIN);
        ids.put(Prayer.ULTIMATE_STRENGTH, SpriteID.PRAYER_ULTIMATE_STRENGTH);
        ids.put(Prayer.INCREDIBLE_REFLEXES, SpriteID.PRAYER_INCREDIBLE_REFLEXES);
        ids.put(Prayer.PROTECT_FROM_MAGIC, SpriteID.PRAYER_PROTECT_FROM_MAGIC);
        ids.put(Prayer.PROTECT_FROM_MISSILES, SpriteID.PRAYER_PROTECT_FROM_MISSILES);
        ids.put(Prayer.PROTECT_FROM_MELEE, SpriteID.PRAYER_PROTECT_FROM_MELEE);
        ids.put(Prayer.EAGLE_EYE, SpriteID.PRAYER_EAGLE_EYE);
        ids.put(Prayer.MYSTIC_MIGHT, SpriteID.PRAYER_MYSTIC_MIGHT);
        ids.put(Prayer.RETRIBUTION, SpriteID.PRAYER_RETRIBUTION);
        ids.put(Prayer.REDEMPTION, SpriteID.PRAYER_REDEMPTION);
        ids.put(Prayer.SMITE, SpriteID.PRAYER_SMITE);
        ids.put(Prayer.CHIVALRY, SpriteID.PRAYER_CHIVALRY);
        ids.put(Prayer.DEADEYE, SpriteID.PRAYER_DEADEYE);
        ids.put(Prayer.MYSTIC_VIGOUR, SpriteID.PRAYER_MYSTIC_VIGOUR);
        ids.put(Prayer.PIETY, SpriteID.PRAYER_PIETY);
        ids.put(Prayer.PRESERVE, SpriteID.PRAYER_PRESERVE);
        ids.put(Prayer.RIGOUR, SpriteID.PRAYER_RIGOUR);
        ids.put(Prayer.AUGURY, SpriteID.PRAYER_AUGURY);
        ids.put(Prayer.RP_REJUVENATION, SpriteID.PRAYER_RP_REJUVENATION);
        ids.put(Prayer.RP_ANCIENT_STRENGTH, SpriteID.PRAYER_RP_ANCIENT_STRENGTH);
        ids.put(Prayer.RP_ANCIENT_SIGHT, SpriteID.PRAYER_RP_ANCIENT_SIGHT);
        ids.put(Prayer.RP_ANCIENT_WILL, SpriteID.PRAYER_RP_ANCIENT_WILL);
        ids.put(Prayer.RP_PROTECT_ITEM, SpriteID.PRAYER_RP_PROTECT_ITEM);
        ids.put(Prayer.RP_RUINOUS_GRACE, SpriteID.PRAYER_RP_RUINOUS_GRACE);
        ids.put(Prayer.RP_DAMPEN_MAGIC, SpriteID.PRAYER_RP_DAMPEN_MAGIC);
        ids.put(Prayer.RP_DAMPEN_RANGED, SpriteID.PRAYER_RP_DAMPEN_RANGED);
        ids.put(Prayer.RP_DAMPEN_MELEE, SpriteID.PRAYER_RP_DAMPEN_MELEE);
        ids.put(Prayer.RP_TRINITAS, SpriteID.PRAYER_RP_TRINITAS);
        ids.put(Prayer.RP_BERSERKER, SpriteID.PRAYER_RP_BERSERKER);
        ids.put(Prayer.RP_PURGE, SpriteID.PRAYER_RP_PURGE);
        ids.put(Prayer.RP_METABOLISE, SpriteID.PRAYER_RP_METABOLISE);
        ids.put(Prayer.RP_REBUKE, SpriteID.PRAYER_RP_REBUKE);
        ids.put(Prayer.RP_VINDICATION, SpriteID.PRAYER_RP_VINDICATION);
        ids.put(Prayer.RP_DECIMATE, SpriteID.PRAYER_RP_DECIMATE);
        ids.put(Prayer.RP_ANNIHILATE, SpriteID.PRAYER_RP_ANNIHILATE);
        ids.put(Prayer.RP_VAPORISE, SpriteID.PRAYER_RP_VAPORISE);
        ids.put(Prayer.RP_FUMUS_VOW, SpriteID.PRAYER_RP_FUMUS_VOW);
        ids.put(Prayer.RP_UMBRA_VOW, SpriteID.PRAYER_RP_UMBRAS_VOW);
        ids.put(Prayer.RP_CRUORS_VOW, SpriteID.PRAYER_RP_CRUORS_VOW);
        ids.put(Prayer.RP_GLACIES_VOW, SpriteID.PRAYER_RP_GLACIES_VOW);
        ids.put(Prayer.RP_WRATH, SpriteID.PRAYER_RP_WRATH);
        ids.put(Prayer.RP_INTENSIFY, SpriteID.PRAYER_RP_INTENSIFY);
        return ids;
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

        if (isOrbGrid()) {
            return renderOrbGrid(graphics, members, localPlayer.getName(), extraCount);
        }
        if (isScoreboard()) {
            return renderScoreboard(graphics, members, localPlayer.getName(), extraCount);
        }

        graphics.setFont(scaledFont(graphics));

        Map<RosterMember, Boolean> offlineByMember = new LinkedHashMap<>();
        for (RosterMember member : members) {
            boolean self = member.name.equalsIgnoreCase(localPlayer.getName());
            offlineByMember.put(member, !self && isOffline(member));
        }

        int height = padding;
        for (RosterMember member : members) {
            height += memberHeight(member, offlineByMember.get(member));
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
            boolean offline = offlineByMember.get(member);
            boolean outOfVicinity = !self && !offline && config.partyOverlayFadeOutOfVicinity()
                    && !withinVicinity(member, config.partyOverlayVicinityFadeTiles());
            int startY = y;
            y = drawMember(graphics, member, y, offline, offline || outOfVicinity);
            lastRenderedRows.put(new Rectangle(0, startY, PANEL_WIDTH, y - startY), member);
        }

        if (extraCount > 0) {
            graphics.setColor(MUTED_TEXT);
            FontMetrics footerMetrics = graphics.getFontMetrics();
            int footerBaseline = y + (lineHeight + footerMetrics.getAscent() - footerMetrics.getDescent()) / 2;
            graphics.drawString("+" + extraCount + " more", padding + 6, footerBaseline);
        }

        return new Dimension(PANEL_WIDTH, height);
    }

    /**
     * Orb Grid's render path: members tile as circular status orbs left-to-right, wrapping into
     * new rows, instead of stacking as labeled bar rows. Mirrors render()'s general shape (compute
     * height, draw chrome, draw members, record hit rects, draw overflow footer) but the geometry
     * is grid math instead of row math, so it doesn't share applyScale()'s padding/lineHeight/etc.
     */
    private Dimension renderOrbGrid(Graphics2D graphics, List<RosterMember> members, String localPlayerName, int extraCount) {
        int cols = Math.max(1, Math.min(ORB_MAX_COLS, members.size()));
        int rows = (int) Math.ceil(members.size() / (double) cols);
        int width = ORB_PADDING * 2 + cols * (ORB_SIZE + ORB_GAP) - ORB_GAP;
        int gridHeight = rows * (ORB_SIZE + ORB_GAP) - ORB_GAP;
        int footerHeight = extraCount > 0 ? ORB_FOOTER_HEIGHT : 0;
        int height = ORB_PADDING * 2 + gridHeight + footerHeight;

        drawChrome(graphics, width, height);
        graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, ORB_SIZE * 0.28f));

        lastRenderedRows.clear();
        for (int i = 0; i < members.size(); i++) {
            RosterMember member = members.get(i);
            boolean self = member.name.equalsIgnoreCase(localPlayerName);
            boolean offline = !self && isOffline(member);
            boolean outOfVicinity = !self && !offline && config.partyOverlayFadeOutOfVicinity()
                    && !withinVicinity(member, config.partyOverlayVicinityFadeTiles());

            int col = i % cols;
            int row = i / cols;
            int cx = ORB_PADDING + col * (ORB_SIZE + ORB_GAP) + ORB_SIZE / 2;
            int cy = ORB_PADDING + row * (ORB_SIZE + ORB_GAP) + ORB_SIZE / 2;

            drawOrb(graphics, cx, cy, member, offline, offline || outOfVicinity);
            lastRenderedRows.put(new Rectangle(cx - ORB_SIZE / 2, cy - ORB_SIZE / 2, ORB_SIZE, ORB_SIZE), member);
        }

        if (extraCount > 0) {
            graphics.setColor(MUTED_TEXT);
            FontMetrics metrics = graphics.getFontMetrics();
            int footerY = ORB_PADDING + gridHeight + (footerHeight + metrics.getAscent() - metrics.getDescent()) / 2;
            graphics.drawString("+" + extraCount + " more", ORB_PADDING, footerY);
        }

        return new Dimension(width, height);
    }

    /**
     * One member's orb: an HP-ratio ring (track when hidden/empty, filled clockwise from the top
     * otherwise), a color-filled core with 2-letter initials, three tick pips for Prayer/Run/Spec
     * lit proportionally to their ratio, and a target badge - each piece skipped per its own
     * partyOverlayHideX() toggle. Offline members render track-only/desaturated with no pips or
     * badge, matching the grayscale-and-fade convention the row tiers already use for offline.
     */
    private void drawOrb(Graphics2D graphics, int cx, int cy, RosterMember member, boolean offline, boolean faded) {
        java.awt.Composite originalComposite = graphics.getComposite();
        if (faded) {
            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, OFFLINE_ALPHA));
        }

        float ringStroke = ORB_SIZE * 0.09f;
        float ringR = ORB_SIZE / 2f - ringStroke / 2f;
        Object previousStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(ringStroke));

        graphics.setColor(TRACK_COLOR);
        graphics.draw(new Arc2D.Double(cx - ringR, cy - ringR, ringR * 2, ringR * 2, 0, 360, Arc2D.OPEN));

        if (!offline && !config.partyOverlayHideHp()) {
            double hpRatio = ratio(member.hp, member.maxHp);
            graphics.setColor(HP_COLOR);
            graphics.draw(new Arc2D.Double(cx - ringR, cy - ringR, ringR * 2, ringR * 2, 90, -360 * hpRatio, Arc2D.OPEN));
        }
        graphics.setStroke((java.awt.Stroke) previousStroke);

        int coreR = Math.round(ORB_SIZE / 2f - ringStroke * 2f);
        Color coreColor = offline ? toGrayscale(memberColor(member.color)) : memberColor(member.color);
        graphics.setColor(coreColor);
        graphics.fillOval(cx - coreR, cy - coreR, coreR * 2, coreR * 2);

        String initials = member.name.length() >= 2 ? member.name.substring(0, 2).toUpperCase() : member.name.toUpperCase();
        graphics.setColor(ORB_INITIALS_COLOR);
        FontMetrics metrics = graphics.getFontMetrics();
        float textX = cx - metrics.stringWidth(initials) / 2f;
        float textY = cy + (metrics.getAscent() - metrics.getDescent()) / 2f;
        graphics.drawString(initials, textX, textY);

        if (!offline) {
            drawOrbPips(graphics, cx, cy, member, faded ? OFFLINE_ALPHA : 1f);

            if (!config.partyOverlayHideTarget() && member.targetName != null && !member.targetName.isEmpty()) {
                boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
                double bx = cx + ORB_SIZE / 2.0 * 0.72;
                double by = cy - ORB_SIZE / 2.0 * 0.72;
                graphics.setColor(isEnemy ? ORB_TARGET_ENEMY : ORB_TARGET_NEUTRAL);
                graphics.fillOval((int) bx - 3, (int) by - 3, 6, 6);
                graphics.setColor(BG_BASE);
                graphics.drawOval((int) bx - 3, (int) by - 3, 6, 6);
            }
        }

        if (faded) {
            graphics.setComposite(originalComposite);
        }
    }

    /** The three Prayer/Run/Spec pips along an orb's bottom arc, each drawn only if its metric isn't hidden. */
    private void drawOrbPips(Graphics2D graphics, int cx, int cy, RosterMember member, float outerAlpha) {
        List<Color> colors = new ArrayList<>(3);
        List<Double> ratios = new ArrayList<>(3);
        if (!config.partyOverlayHidePrayer()) {
            colors.add(PRAYER_COLOR);
            ratios.add(ratio(member.prayer, member.maxPrayer));
        }
        if (!config.partyOverlayHideRun()) {
            colors.add(RUN_COLOR);
            ratios.add(ratio(member.runEnergy, 100));
        }
        if (!config.partyOverlayHideSpec()) {
            colors.add(SPEC_COLOR);
            ratios.add(ratio(member.specEnergy, 100));
        }
        if (colors.isEmpty()) {
            return;
        }

        double baseAngle = Math.PI / 2;
        double spread = 0.75;
        double pipR = ORB_SIZE / 2.0 + ORB_PIP_GAP + ORB_PIP_RADIUS;
        for (int i = 0; i < colors.size(); i++) {
            double angle = colors.size() == 1 ? baseAngle
                    : baseAngle - spread / 2 + spread * (i / (double) (colors.size() - 1));
            int px = (int) Math.round(cx + Math.cos(angle) * pipR);
            int py = (int) Math.round(cy + Math.sin(angle) * pipR);

            graphics.setColor(TRACK_COLOR);
            graphics.fillOval(px - ORB_PIP_RADIUS, py - ORB_PIP_RADIUS, ORB_PIP_RADIUS * 2, ORB_PIP_RADIUS * 2);

            double r = ratios.get(i);
            if (r > 0.05) {
                float alpha = (float) Math.max(0.35, r) * outerAlpha;
                java.awt.Composite pipComposite = graphics.getComposite();
                graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                graphics.setColor(colors.get(i));
                graphics.fillOval(px - ORB_PIP_RADIUS, py - ORB_PIP_RADIUS, ORB_PIP_RADIUS * 2, ORB_PIP_RADIUS * 2);
                graphics.setComposite(pipComposite);
            }
        }
    }

    /**
     * Scoreboard's render path: members sit in narrow side-by-side columns, wrapping into a new
     * strip below past SCOREBOARD_MAX_COLS, instead of stacking as rows or tiling as a 2D grid.
     * Mirrors renderOrbGrid's shape (content-sized panel, draw chrome, draw members, record hit
     * rects, draw overflow footer).
     */
    private Dimension renderScoreboard(Graphics2D graphics, List<RosterMember> members, String localPlayerName, int extraCount) {
        int cols = Math.max(1, Math.min(SCOREBOARD_MAX_COLS, members.size()));
        int rows = (int) Math.ceil(members.size() / (double) cols);
        int stripHeight = SCOREBOARD_BAR_HEIGHT + SCOREBOARD_PIP_ROW_GAP + SCOREBOARD_PIP_SIZE + SCOREBOARD_LABEL_GAP + 8;
        int width = SCOREBOARD_PADDING * 2 + cols * (SCOREBOARD_COL_WIDTH + SCOREBOARD_GAP) - SCOREBOARD_GAP;
        int gridHeight = rows * (stripHeight + SCOREBOARD_GAP) - SCOREBOARD_GAP + SCOREBOARD_TARGET_HEADROOM;
        int footerHeight = extraCount > 0 ? ORB_FOOTER_HEIGHT : 0;
        int height = SCOREBOARD_PADDING * 2 + gridHeight + footerHeight;

        drawChrome(graphics, width, height);
        graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, SCOREBOARD_FONT_SIZE));

        lastRenderedRows.clear();
        for (int i = 0; i < members.size(); i++) {
            RosterMember member = members.get(i);
            boolean self = member.name.equalsIgnoreCase(localPlayerName);
            boolean offline = !self && isOffline(member);
            boolean outOfVicinity = !self && !offline && config.partyOverlayFadeOutOfVicinity()
                    && !withinVicinity(member, config.partyOverlayVicinityFadeTiles());

            int col = i % cols;
            int row = i / cols;
            int x = SCOREBOARD_PADDING + col * (SCOREBOARD_COL_WIDTH + SCOREBOARD_GAP);
            int y = SCOREBOARD_PADDING + row * (stripHeight + SCOREBOARD_GAP) + SCOREBOARD_TARGET_HEADROOM;

            drawScoreColumn(graphics, x, y, member, offline, offline || outOfVicinity);
            lastRenderedRows.put(new Rectangle(x, y - SCOREBOARD_TARGET_HEADROOM, SCOREBOARD_COL_WIDTH, stripHeight + SCOREBOARD_TARGET_HEADROOM), member);
        }

        if (extraCount > 0) {
            graphics.setColor(MUTED_TEXT);
            FontMetrics metrics = graphics.getFontMetrics();
            int footerY = SCOREBOARD_PADDING + gridHeight + (footerHeight + metrics.getAscent() - metrics.getDescent()) / 2;
            graphics.drawString("+" + extraCount + " more", SCOREBOARD_PADDING, footerY);
        }

        return new Dimension(width, height);
    }

    /**
     * One member's column: an optional target-flag triangle above the bar, a vertical HP meter
     * (the one thing meant to read at a glance from across the screen), a row of Prayer/Run/Spec
     * tally pips, and initials below - each piece skipped per its own partyOverlayHideX() toggle.
     * Offline columns show the HP track only, dimmed grayscale initials, and no pips or flag.
     */
    private void drawScoreColumn(Graphics2D graphics, int x, int y, RosterMember member, boolean offline, boolean faded) {
        java.awt.Composite originalComposite = graphics.getComposite();
        if (faded) {
            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, OFFLINE_ALPHA));
        }

        int barX = x + (SCOREBOARD_COL_WIDTH - SCOREBOARD_BAR_WIDTH) / 2;

        if (!offline && !config.partyOverlayHideTarget() && member.targetName != null && !member.targetName.isEmpty()) {
            boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
            int midX = x + SCOREBOARD_COL_WIDTH / 2;
            int[] xs = {midX, midX - 4, midX + 4};
            int[] ys = {y - 6, y - 1, y - 1};
            graphics.setColor(isEnemy ? ORB_TARGET_ENEMY : ORB_TARGET_NEUTRAL);
            graphics.fillPolygon(xs, ys, 3);
        }

        java.awt.geom.RoundRectangle2D.Float track = new java.awt.geom.RoundRectangle2D.Float(
                barX, y, SCOREBOARD_BAR_WIDTH, SCOREBOARD_BAR_HEIGHT, 6, 6);
        graphics.setColor(TRACK_COLOR);
        graphics.fill(track);

        if (!offline && !config.partyOverlayHideHp()) {
            double hpRatio = ratio(member.hp, member.maxHp);
            int fillHeight = (int) Math.round(SCOREBOARD_BAR_HEIGHT * hpRatio);
            Object previousClip = graphics.getClip();
            graphics.clip(track);
            graphics.setColor(HP_COLOR);
            graphics.fillRect(barX, y + SCOREBOARD_BAR_HEIGHT - fillHeight, SCOREBOARD_BAR_WIDTH, fillHeight);
            graphics.setClip((java.awt.Shape) previousClip);
        }

        int py = y + SCOREBOARD_BAR_HEIGHT + SCOREBOARD_PIP_ROW_GAP;
        if (!offline) {
            drawScorePips(graphics, x, py, member, faded ? OFFLINE_ALPHA : 1f);
        }

        int labelY = py + SCOREBOARD_PIP_SIZE + SCOREBOARD_LABEL_GAP;
        FontMetrics metrics = graphics.getFontMetrics();
        String initials = member.name.length() >= 2 ? member.name.substring(0, 2).toUpperCase() : member.name.toUpperCase();
        graphics.setColor(offline ? toGrayscale(memberColor(member.color)) : memberColor(member.color));
        drawBoldString(graphics, initials, x + (SCOREBOARD_COL_WIDTH - metrics.stringWidth(initials)) / 2f, labelY);

        if (faded) {
            graphics.setComposite(originalComposite);
        }
    }

    /** The Prayer/Run/Spec tally pips under a scoreboard column's HP bar, each skipped if its metric is hidden. */
    private void drawScorePips(Graphics2D graphics, int columnX, int y, RosterMember member, float outerAlpha) {
        List<Color> colors = new ArrayList<>(3);
        List<Double> ratios = new ArrayList<>(3);
        if (!config.partyOverlayHidePrayer()) {
            colors.add(PRAYER_COLOR);
            ratios.add(ratio(member.prayer, member.maxPrayer));
        }
        if (!config.partyOverlayHideRun()) {
            colors.add(RUN_COLOR);
            ratios.add(ratio(member.runEnergy, 100));
        }
        if (!config.partyOverlayHideSpec()) {
            colors.add(SPEC_COLOR);
            ratios.add(ratio(member.specEnergy, 100));
        }
        if (colors.isEmpty()) {
            return;
        }

        int totalWidth = colors.size() * SCOREBOARD_PIP_SIZE + (colors.size() - 1) * SCOREBOARD_PIP_H_GAP;
        int px = columnX + (SCOREBOARD_COL_WIDTH - totalWidth) / 2;
        for (int i = 0; i < colors.size(); i++) {
            graphics.setColor(TRACK_COLOR);
            graphics.fillRect(px, y, SCOREBOARD_PIP_SIZE, SCOREBOARD_PIP_SIZE);

            double r = ratios.get(i);
            if (r > 0.05) {
                float alpha = (float) Math.max(0.35, r) * outerAlpha;
                java.awt.Composite pipComposite = graphics.getComposite();
                graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                graphics.setColor(colors.get(i));
                graphics.fillRect(px, y, SCOREBOARD_PIP_SIZE, SCOREBOARD_PIP_SIZE);
                graphics.setComposite(pipComposite);
            }
            px += SCOREBOARD_PIP_SIZE + SCOREBOARD_PIP_H_GAP;
        }
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
        switch (config.partyOverlayScale()) {
            case MINIMAL:
                padding = MINIMAL_PADDING;
                memberGap = MINIMAL_MEMBER_GAP;
                lineHeight = MINIMAL_LINE_HEIGHT;
                barHeight = MINIMAL_BAR_HEIGHT;
                barGap = MINIMAL_BAR_GAP;
                prayerIconSize = MINIMAL_PRAYER_ICON_SIZE;
                prayerIconRowHeight = MINIMAL_PRAYER_ICON_ROW_HEIGHT;
                targetStripHeight = MINIMAL_TARGET_STRIP_HEIGHT;
                break;
            case SUPER_COMPACT:
                padding = SUPER_COMPACT_PADDING;
                memberGap = SUPER_COMPACT_MEMBER_GAP;
                lineHeight = SUPER_COMPACT_LINE_HEIGHT;
                barHeight = SUPER_COMPACT_BAR_HEIGHT;
                barGap = SUPER_COMPACT_BAR_GAP;
                prayerIconSize = SUPER_COMPACT_PRAYER_ICON_SIZE;
                prayerIconRowHeight = SUPER_COMPACT_PRAYER_ICON_ROW_HEIGHT;
                targetStripHeight = barHeight;
                break;
            case COMPACT:
                padding = COMPACT_PADDING;
                memberGap = COMPACT_MEMBER_GAP;
                lineHeight = COMPACT_LINE_HEIGHT;
                barHeight = COMPACT_BAR_HEIGHT;
                barGap = COMPACT_BAR_GAP;
                prayerIconSize = COMPACT_PRAYER_ICON_SIZE;
                prayerIconRowHeight = COMPACT_PRAYER_ICON_ROW_HEIGHT;
                targetStripHeight = barHeight;
                break;
            case NORMAL:
            default:
                padding = NORMAL_PADDING;
                memberGap = NORMAL_MEMBER_GAP;
                lineHeight = NORMAL_LINE_HEIGHT;
                barHeight = NORMAL_BAR_HEIGHT;
                barGap = NORMAL_BAR_GAP;
                prayerIconSize = NORMAL_PRAYER_ICON_SIZE;
                prayerIconRowHeight = NORMAL_PRAYER_ICON_ROW_HEIGHT;
                targetStripHeight = barHeight;
                break;
        }
    }

    private boolean isMinimal() {
        return config.partyOverlayScale() == GroupScapeTrackerConfig.PartyOverlayScale.MINIMAL;
    }

    private boolean isOrbGrid() {
        return config.partyOverlayScale() == GroupScapeTrackerConfig.PartyOverlayScale.ORB_GRID;
    }

    private boolean isScoreboard() {
        return config.partyOverlayScale() == GroupScapeTrackerConfig.PartyOverlayScale.SCOREBOARD;
    }

    /**
     * The base runescape-small font (~16pt) is sized for Normal's 13px line height; a flat -1/-2pt
     * offset from it still overflows Compact/Super Compact's 9-11px rows, which is what caused
     * labels to overlap the bar below them. Instead, shrink the font in half-point steps until its
     * actual metrics (not a guessed offset) fit the tightest row this tier draws text into - the
     * name row (lineHeight) for every tier, plus the bar row (barHeight + barGap) for tiers that
     * label their bars (everything except Minimal, whose bars are unlabeled fills).
     */
    private Font scaledFont(Graphics2D graphics) {
        Font base = FontManager.getRunescapeSmallFont();
        if (config.partyOverlayScale() == GroupScapeTrackerConfig.PartyOverlayScale.NORMAL) {
            return base;
        }

        int targetHeight = isMinimal() ? lineHeight : Math.min(lineHeight, barHeight + barGap);

        Font candidate = base;
        float size = base.getSize2D();
        FontMetrics metrics = graphics.getFontMetrics(candidate);
        while (metrics.getHeight() > targetHeight && size > 6f) {
            size -= 0.5f;
            candidate = base.deriveFont(size);
            metrics = graphics.getFontMetrics(candidate);
        }
        return candidate;
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
            if (config.partyOverlayHideOutOfVicinity()
                    && !isOffline(member)
                    && !withinVicinity(member, config.partyOverlayVicinityHideTiles())) {
                continue;
            }
            members.add(member);
        }

        return members;
    }

    /**
     * True if {@code member}'s actor is loaded in the local client's scene and within
     * {@code tiles} of the local player. The roster carries no synced world position, so distance
     * can only be confirmed for members RuneLite has actually rendered nearby - an unloaded actor
     * (different world, out of render distance) is treated as not within vicinity.
     */
    private boolean withinVicinity(RosterMember member, int tiles) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }
        for (Player player : client.getPlayers()) {
            if (player == null || player.getName() == null) {
                continue;
            }
            if (!player.getName().equalsIgnoreCase(member.name)) {
                continue;
            }
            return player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= tiles;
        }
        return false;
    }

    /** Reads the local player's own vitals straight from Client, avoiding any WebSocket round-trip. */
    private RosterMember buildSelfRow(String name) {
        return LocalRosterMemberFactory.build(client, dialogueTracker, rosterState);
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

    private int memberHeight(RosterMember member, boolean offline) {
        int height = nameRowHeight(member, offline); // name row

        if (offline) {
            return height + memberGap;
        }

        if (isMinimal()) {
            if (hasVisibleClusterBar()) height += barHeight + barGap;
            if (!config.partyOverlayHideTarget()) height += targetStripHeight + barGap;
            return height + memberGap;
        }

        if (!config.partyOverlayHideHp()) height += barHeight + barGap;
        if (!config.partyOverlayHidePrayer()) {
            height += barHeight + barGap;
        }
        if (!config.partyOverlayHideRun()) height += barHeight + barGap;
        if (!config.partyOverlayHideSpec()) height += barHeight + barGap;
        if (!config.partyOverlayHideTarget()) height += barHeight + barGap;
        return height + memberGap;
    }

    /**
     * Height of the name row, which now doubles as the active-prayer-icon row (icons sit between
     * the name and the right-aligned world/offline text), so it grows past {@link #lineHeight}
     * when the icons are taller than the text.
     */
    private int nameRowHeight(RosterMember member, boolean offline) {
        if (offline || config.partyOverlayHidePrayer() || config.partyOverlayHidePrayerIcons()
                || visibleActivePrayers(member).isEmpty()) {
            return lineHeight;
        }
        return Math.max(lineHeight, prayerIconRowHeight);
    }

    /** True if at least one of HP/Prayer/Run/Spec is visible, i.e. the minimal cluster row has something to draw. */
    private boolean hasVisibleClusterBar() {
        return !config.partyOverlayHideHp() || !config.partyOverlayHidePrayer()
                || !config.partyOverlayHideRun() || !config.partyOverlayHideSpec();
    }

    private int drawMember(Graphics2D graphics, RosterMember member, int y, boolean offline, boolean faded) {
        int startY = y;
        Color stripeColor = memberColor(member.color);

        float alpha = faded ? OFFLINE_ALPHA : 1f;
        java.awt.Composite originalComposite = graphics.getComposite();
        if (faded) {
            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        graphics.setColor(stripeColor);
        graphics.fillRect(padding, y, 2, memberHeight(member, offline) - memberGap);

        int textX = padding + 6;
        int barWidth = PANEL_WIDTH - textX - padding;

        int nameRowHeight = nameRowHeight(member, offline);
        FontMetrics metrics = graphics.getFontMetrics();
        int nameBaseline = y + (nameRowHeight + metrics.getAscent() - metrics.getDescent()) / 2;

        graphics.setColor(offline ? toGrayscale(TEXT) : TEXT);
        graphics.drawString(member.name, textX, nameBaseline);

        String status = offline ? "Offline" : (!config.partyOverlayHideWorld() && member.world != null ? "W" + member.world : null);
        if (status != null) {
            graphics.drawString(status, PANEL_WIDTH - padding - metrics.stringWidth(status), nameBaseline);
        }

        if (!offline && !config.partyOverlayHidePrayer() && !config.partyOverlayHidePrayerIcons()) {
            List<String> activePrayers = visibleActivePrayers(member);
            if (!activePrayers.isEmpty()) {
                int nameEndX = textX + metrics.stringWidth(member.name) + 6;
                int statusStartX = status != null
                        ? PANEL_WIDTH - padding - metrics.stringWidth(status) - 6
                        : PANEL_WIDTH - padding;
                int availableWidth = Math.max(0, statusStartX - nameEndX);
                int iconsY = y + (nameRowHeight - prayerIconSize) / 2;
                drawPrayerIcons(graphics, nameEndX, iconsY, availableWidth, activePrayers);
            }
        }

        y += nameRowHeight;

        if (offline) {
            if (faded) {
                graphics.setComposite(originalComposite);
            }
            return startY + memberHeight(member, offline);
        }

        if (isMinimal()) {
            if (hasVisibleClusterBar()) {
                drawMinimalCluster(graphics, textX, y, barWidth, member);
                y += barHeight + barGap;
            }

            if (!config.partyOverlayHideTarget()) {
                drawMinimalTargetStrip(graphics, textX, y, barWidth, member);
                y += targetStripHeight + barGap;
            }

            if (faded) {
                graphics.setComposite(originalComposite);
            }

            return startY + memberHeight(member, offline);
        }

        if (!config.partyOverlayHideHp()) {
            drawBar(graphics, textX, y, barWidth, "HP", member.hp, member.maxHp, HP_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHidePrayer()) {
            drawBar(graphics, textX, y, barWidth, "Pr", member.prayer, member.maxPrayer, PRAYER_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHideRun()) {
            drawBar(graphics, textX, y, barWidth, "Run", member.runEnergy, 100, RUN_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHideSpec()) {
            drawBar(graphics, textX, y, barWidth, "Sp", member.specEnergy, 100, SPEC_COLOR);
            y += barHeight + barGap;
        }

        if (!config.partyOverlayHideTarget()) {
            drawTargetBar(graphics, textX, y, barWidth, member);
            y += barHeight + barGap;
        }

        if (faded) {
            graphics.setComposite(originalComposite);
        }

        return startY + memberHeight(member, offline);
    }

    private void drawBar(Graphics2D graphics, int x, int y, int width, String label, Integer value, Integer max, Color color) {
        int textBaseline = barTextBaseline(graphics, y);

        graphics.setColor(MUTED_TEXT);
        graphics.drawString(label, x, textBaseline);

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
            graphics.drawString(String.valueOf(clampedValue), barX + barWidth + 4, textBaseline);
        } else {
            graphics.setColor(MUTED_TEXT);
            graphics.drawString("--", barX + barWidth + 4, textBaseline);
        }
    }

    /** Bottom-aligns bar label/value text inside a barHeight-tall row using the current font's actual descent. */
    private int barTextBaseline(Graphics2D graphics, int y) {
        return y + barHeight - graphics.getFontMetrics().getDescent();
    }

    /**
     * Renders the "target" row as an HP-style bar instead of plain text, laid out exactly like
     * {@link #drawBar} (label outside to the left, value outside to the right) so it lines up
     * with the HP/Pr/Run/Sp rows above it. Styled to match the webapp's player-interacting
     * component: red bar filled by HP ratio for an actual combat target (health scale &gt; 0),
     * full gold bar with no HP value for a neutral interaction (banking, talking to an NPC). The
     * target's name renders inside the bar and truncates with an ellipsis instead of overflowing.
     * Row is always drawn — reserving its height even with no target — so the panel doesn't
     * grow/shrink as members enter and leave combat; with no target it falls back to the same
     * muted-track/"--" look {@link #drawBar} uses for missing values, rather than going blank.
     */
    private void drawTargetBar(Graphics2D graphics, int x, int y, int width, RosterMember member) {
        if (member.targetName == null || member.targetName.isEmpty()) {
            drawBar(graphics, x, y, width, "Tgt", null, null, null);
            return;
        }

        boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
        boolean hasRatio = isEnemy && member.targetHealthRatio != null && member.targetHealthRatio >= 0;

        Color fill = isEnemy ? TARGET_COMBAT_FILL : TARGET_NEUTRAL_FILL;
        Color track = isEnemy ? TARGET_COMBAT_TRACK : TARGET_NEUTRAL_TRACK;
        Color border = isEnemy ? TARGET_COMBAT_BORDER : TARGET_NEUTRAL_BORDER;
        Color labelColor = isEnemy ? TARGET_COMBAT_LABEL : TARGET_NEUTRAL_LABEL;

        int textBaseline = barTextBaseline(graphics, y);
        graphics.setColor(labelColor);
        graphics.drawString("Tgt", x, textBaseline);

        FontMetrics metrics = graphics.getFontMetrics();
        String hpText = hasRatio ? targetHealthPercent(member) + "%" : null;
        int valueWidth = hpText != null ? metrics.stringWidth(hpText) + 4 : 0;

        int labelWidth = 22;
        int barX = x + labelWidth;
        int barWidth = Math.max(20, width - labelWidth - valueWidth);

        graphics.setColor(track);
        graphics.fillRect(barX, y, barWidth, barHeight);

        int filledWidth = barWidth;
        if (hasRatio) {
            filledWidth = (int) (targetHealthRatio(member) * barWidth);
        }
        graphics.setColor(fill);
        graphics.fillRect(barX, y, filledWidth, barHeight);

        graphics.setColor(border);
        graphics.drawRect(barX, y, barWidth - 1, barHeight - 1);

        graphics.setColor(TEXT);
        String name = truncateToWidth(metrics, member.targetName, Math.max(0, barWidth - 6));
        graphics.drawString(name, barX + 3, textBaseline);

        if (hasRatio) {
            graphics.setColor(labelColor);
            graphics.drawString(hpText, barX + barWidth + 4, textBaseline);
        }
    }

    /**
     * Minimal tier only: fuses whichever of HP/Prayer/Run/Spec aren't hidden into one row of
     * equal-width segments (color + fill, no label/number) instead of stacking them as separate
     * labeled bars. Hiding a bar via its own config toggle removes its segment and lets the
     * remaining ones grow to fill the freed width, rather than leaving a gap.
     */
    private void drawMinimalCluster(Graphics2D graphics, int x, int y, int width, RosterMember member) {
        List<Color> colors = new ArrayList<>(4);
        List<Double> ratios = new ArrayList<>(4);
        if (!config.partyOverlayHideHp()) {
            colors.add(HP_COLOR);
            ratios.add(ratio(member.hp, member.maxHp));
        }
        if (!config.partyOverlayHidePrayer()) {
            colors.add(PRAYER_COLOR);
            ratios.add(ratio(member.prayer, member.maxPrayer));
        }
        if (!config.partyOverlayHideRun()) {
            colors.add(RUN_COLOR);
            ratios.add(ratio(member.runEnergy, 100));
        }
        if (!config.partyOverlayHideSpec()) {
            colors.add(SPEC_COLOR);
            ratios.add(ratio(member.specEnergy, 100));
        }
        if (colors.isEmpty()) {
            return;
        }

        int segmentCount = colors.size();
        int gapTotal = MINIMAL_CLUSTER_SEGMENT_GAP * (segmentCount - 1);
        int segmentWidth = (width - gapTotal) / segmentCount;

        int segX = x;
        for (int i = 0; i < segmentCount; i++) {
            int w = (i == segmentCount - 1) ? (width - (segX - x)) : segmentWidth;

            graphics.setColor(TRACK_COLOR);
            graphics.fillRect(segX, y, w, barHeight);

            double r = ratios.get(i);
            if (r > 0) {
                graphics.setColor(colors.get(i));
                graphics.fillRect(segX, y, (int) Math.round(w * r), barHeight);
            }

            segX += w + MINIMAL_CLUSTER_SEGMENT_GAP;
        }
    }

    /**
     * Minimal tier only: renders the target row as a thin, label-free strip instead of
     * {@link #drawTargetBar}'s full bar-with-name-and-percentage, matching the cluster row's
     * color-and-fill-only style. Always drawn (muted/empty when there's no target) so the panel
     * doesn't resize as members enter and leave combat, mirroring drawTargetBar's convention.
     */
    private void drawMinimalTargetStrip(Graphics2D graphics, int x, int y, int width, RosterMember member) {
        Color track = TRACK_COLOR;
        Color fill = null;
        double ratio = 1.0;

        if (member.targetName != null && !member.targetName.isEmpty()) {
            boolean isEnemy = member.targetHealthScale != null && member.targetHealthScale > 0;
            boolean hasRatio = isEnemy && member.targetHealthRatio != null && member.targetHealthRatio >= 0;
            track = isEnemy ? TARGET_COMBAT_TRACK : TARGET_NEUTRAL_TRACK;
            fill = isEnemy ? TARGET_COMBAT_FILL : TARGET_NEUTRAL_FILL;
            if (hasRatio) {
                ratio = targetHealthRatio(member);
            }
        }

        graphics.setColor(track);
        graphics.fillRect(x, y, width, targetStripHeight);
        if (fill != null) {
            graphics.setColor(fill);
            graphics.fillRect(x, y, (int) Math.round(width * ratio), targetStripHeight);
        }
    }

    /** Clamped 0-1 ratio for a value/max pair, treating a missing value or non-positive max as empty. */
    private static double ratio(Integer value, Integer max) {
        if (value == null || max == null || max <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1.0, value / (double) max));
    }

    /** Clamped current/scale ratio for a target with a known health scale, mirroring the webapp's player-interacting component. */
    private static double targetHealthRatio(RosterMember member) {
        return Math.max(0, Math.min(1.0, member.targetHealthRatio / (double) member.targetHealthScale));
    }

    private static long targetHealthPercent(RosterMember member) {
        return Math.round(targetHealthRatio(member) * 100);
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

    /**
     * The member's active prayers, with base tri-prayers dropped when their upgraded curse
     * replacement (e.g. Deadeye over Rigour) is active too. Returns an empty list (not just a
     * missing field) so callers can use it directly to decide whether to reserve the icon row.
     */
    private static List<String> visibleActivePrayers(RosterMember member) {
        List<String> raw = member.activePrayers;
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Prayer> active = EnumSet.noneOf(Prayer.class);
        for (String name : raw) {
            Prayer prayer = parsePrayer(name);
            if (prayer != null) {
                active.add(prayer);
            }
        }

        List<String> visible = new ArrayList<>(raw.size());
        for (String name : raw) {
            Prayer prayer = parsePrayer(name);
            Prayer upgrade = prayer != null ? BASE_PRAYER_SUPPRESSED_BY_UPGRADE.get(prayer) : null;
            if (upgrade != null && active.contains(upgrade)) {
                continue;
            }
            visible.add(name);
        }
        return visible;
    }

    /**
     * Draws real prayer-tab sprites for each active prayer, overhead/protection prayers first
     * (gold-tinted), overflowing into a plain "+N" once the row runs out of width.
     */
    private void drawPrayerIcons(Graphics2D graphics, int x, int y, int availableWidth, List<String> activePrayerNames) {
        List<String> ordered = new ArrayList<>(activePrayerNames);
        ordered.sort(Comparator.comparingInt(name -> isOverheadPrayerName(name) ? 0 : 1));

        int maxIcons = Math.max(1, (availableWidth + PRAYER_ICON_GAP) / (prayerIconSize + PRAYER_ICON_GAP));

        int shownCount = ordered.size();
        int overflow = 0;
        if (shownCount > maxIcons) {
            overflow = shownCount - (maxIcons - 1);
            shownCount = maxIcons - 1;
        }

        int drawX = x;
        for (int i = 0; i < shownCount; i++) {
            drawX = drawPrayerIcon(graphics, drawX, y, ordered.get(i));
        }

        if (overflow > 0) {
            graphics.setColor(MUTED_TEXT);
            graphics.drawString("+" + overflow, drawX + 2, y + prayerIconSize - 3);
        }
    }

    /** Draws one prayer's sprite (with an overhead tint behind it if applicable) and returns the x for the next icon. */
    private int drawPrayerIcon(Graphics2D graphics, int x, int y, String prayerName) {
        Prayer prayer = parsePrayer(prayerName);
        BufferedImage sprite = prayer != null ? prayerSprites.get(prayer) : null;

        if (prayer != null && OVERHEAD_PRAYERS.contains(prayer)) {
            graphics.setColor(OVERHEAD_TINT);
            graphics.fillOval(x, y, prayerIconSize, prayerIconSize);
        }

        if (sprite != null) {
            // Sprites aren't all the same aspect ratio - stretching to a square box visibly
            // squishes the wider/taller ones, so scale uniformly and center instead.
            double scale = Math.min(prayerIconSize / (double) sprite.getWidth(), prayerIconSize / (double) sprite.getHeight());
            int drawWidth = (int) Math.round(sprite.getWidth() * scale);
            int drawHeight = (int) Math.round(sprite.getHeight() * scale);
            int drawX = x + (prayerIconSize - drawWidth) / 2;
            int drawY = y + (prayerIconSize - drawHeight) / 2;

            Object previousHint = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(sprite, drawX, drawY, drawWidth, drawHeight, null);
            if (previousHint != null) {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousHint);
            }
        } else {
            graphics.setColor(TRACK_COLOR);
            graphics.fillOval(x, y, prayerIconSize, prayerIconSize);
        }

        return x + prayerIconSize + PRAYER_ICON_GAP;
    }

    private static boolean isOverheadPrayerName(String prayerName) {
        Prayer prayer = parsePrayer(prayerName);
        return prayer != null && OVERHEAD_PRAYERS.contains(prayer);
    }

    private static Prayer parsePrayer(String prayerName) {
        try {
            return Prayer.valueOf(prayerName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Faux-bolds text by drawing it twice with a slight horizontal offset. The RuneScape font is
     * a single-weight embedded TTF, so requesting {@link Font#BOLD} via deriveFont doesn't
     * reliably synthesize an actual bold glyph outline - this stroke-doubling trick is what
     * consistently reads as bold instead.
     */
    private static void drawBoldString(Graphics2D graphics, String text, float x, float y) {
        graphics.drawString(text, x, y);
        graphics.drawString(text, x + 0.6f, y);
    }

    /** Desaturates a color to gray while preserving its alpha and perceived brightness. */
    private static Color toGrayscale(Color c) {
        int gray = (int) Math.round(0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
        return new Color(gray, gray, gray, c.getAlpha());
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

}
