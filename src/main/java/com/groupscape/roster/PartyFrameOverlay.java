package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.NpcDialogueTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
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

    private static final int PRAYER_ICON_GAP = 2;

    private int padding = NORMAL_PADDING;
    private int memberGap = NORMAL_MEMBER_GAP;
    private int lineHeight = NORMAL_LINE_HEIGHT;
    private int barHeight = NORMAL_BAR_HEIGHT;
    private int barGap = NORMAL_BAR_GAP;
    private int prayerIconSize = NORMAL_PRAYER_ICON_SIZE;
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

    // Overhead/protection prayers get a gold accent behind their icon and sort to the front of
    // the active-prayer row, since they're the highest-priority thing to notice mid-fight.
    private static final Set<Prayer> OVERHEAD_PRAYERS = EnumSet.of(
            Prayer.PROTECT_FROM_MELEE, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MAGIC,
            Prayer.RETRIBUTION, Prayer.REDEMPTION, Prayer.SMITE
    );
    private static final Color OVERHEAD_TINT = new Color(232, 197, 71, 130);

    // Activating one of these "upgraded" curses also flags its base tri-prayer as active in the
    // client's prayer state, so without this the row would show both e.g. Rigour and Deadeye at
    // once even though only Deadeye is actually selected in-game.
    private static final Map<Prayer, Prayer> BASE_PRAYER_SUPPRESSED_BY_UPGRADE = new EnumMap<>(Prayer.class);
    static {
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.RIGOUR, Prayer.DEADEYE);
        BASE_PRAYER_SUPPRESSED_BY_UPGRADE.put(Prayer.AUGURY, Prayer.MYSTIC_VIGOUR);
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
            boolean outOfVicinity = !self && !offline && config.partyOverlayFadeOutOfVicinity()
                    && !withinVicinity(member, config.partyOverlayVicinityFadeTiles());
            int startY = y;
            y = drawMember(graphics, member, y, offline || outOfVicinity);
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
        prayerIconSize = compact ? COMPACT_PRAYER_ICON_SIZE : NORMAL_PRAYER_ICON_SIZE;
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
            if (!config.partyOverlayHidePrayerIcons() && !visibleActivePrayers(member).isEmpty()) {
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

    private int drawMember(Graphics2D graphics, RosterMember member, int y, boolean faded) {
        int startY = y;
        Color stripeColor = memberColor(member.color);

        float alpha = faded ? OFFLINE_ALPHA : 1f;
        java.awt.Composite originalComposite = graphics.getComposite();
        if (faded) {
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

            if (!config.partyOverlayHidePrayerIcons()) {
                List<String> activePrayers = visibleActivePrayers(member);
                if (!activePrayers.isEmpty()) {
                    drawPrayerIcons(graphics, textX, y, activePrayers);
                    y += prayerIconRowHeight;
                }
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

        if (faded) {
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
        graphics.drawString(name, barX + 3, y + barHeight - 2);

        if (hasRatio) {
            graphics.setColor(labelColor);
            graphics.drawString(hpText, barX + barWidth + 4, y + barHeight - 2);
        }
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
    private void drawPrayerIcons(Graphics2D graphics, int x, int y, List<String> activePrayerNames) {
        List<String> ordered = new ArrayList<>(activePrayerNames);
        ordered.sort(Comparator.comparingInt(name -> isOverheadPrayerName(name) ? 0 : 1));

        int availableWidth = PANEL_WIDTH - x - padding;
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
