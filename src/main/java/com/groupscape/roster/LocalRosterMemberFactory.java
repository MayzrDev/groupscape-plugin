package com.groupscape.roster;

import com.groupscape.NpcDialogueTracker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Builds a {@link RosterMember} for the local player straight from {@link Client}, bypassing the
 * roster/websocket entirely. Shared by {@link PartyFrameOverlay} and the sidepanel's roster list
 * so both show a live "you" row that works even with no server connection.
 */
public final class LocalRosterMemberFactory {
    private LocalRosterMemberFactory() {
    }

    public static RosterMember build(Client client, NpcDialogueTracker dialogueTracker) {
        return build(client, dialogueTracker, null);
    }

    /**
     * @param rosterState if non-null, used to look up the local player's server-assigned color
     * (the same admin-assigned helmet colour shown for every other member) instead of the
     * placeholder gold fallback, since the local player's own row bypasses the roster otherwise.
     */
    public static RosterMember build(Client client, NpcDialogueTracker dialogueTracker, RosterState rosterState) {
        Player player = client.getLocalPlayer();
        if (player == null || player.getName() == null) {
            return null;
        }

        RosterMember self = new RosterMember(player.getName());
        RosterMember known = rosterState != null ? rosterState.findByName(player.getName()) : null;
        self.color = known != null ? known.color : "#FFD700";
        self.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        self.maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
        self.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
        self.maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        self.runEnergy = client.getEnergy() / 100;
        self.specEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
        self.world = client.getWorld();
        self.lastHeartbeatAt = Instant.now();
        self.activePrayers = activePrayerNames(client);
        applyLocalTarget(self, client, dialogueTracker);
        return self;
    }

    /**
     * Fills the self row's target fields straight from Client, the same signals
     * {@code RichPresenceState} used to build its text ("Fighting X" / "Talking to X" /
     * "Browsing the bank"), but kept as structured name+ratio+scale so the bar can render an
     * actual HP fill instead of a static line of text.
     */
    private static void applyLocalTarget(RosterMember self, Client client, NpcDialogueTracker dialogueTracker) {
        Player player = client.getLocalPlayer();

        Actor interacting = player.getInteracting();
        if (interacting != null && interacting.getName() != null) {
            self.targetName = interacting.getName();

            // Non-combat NPCs (bankers, quest givers, Tool Leprechauns, etc.) can still flash a
            // stale/default healthbar ratio from RuneLite for a tick even though they're not
            // actually fightable - combat level isn't reliable here since some non-attackable
            // NPCs still report one. Whether "Attack" is actually a menu option is the signal
            // that can't fluctuate tick to tick.
            boolean combatCapable = !(interacting instanceof NPC) || isAttackable((NPC) interacting);
            if (combatCapable) {
                self.targetHealthScale = interacting.getHealthScale();
                self.targetHealthRatio = interacting.getHealthRatio();
            } else {
                self.targetHealthScale = 0;
                self.targetHealthRatio = -1;
            }
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

        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (bankItems != null && !bankItems.isHidden()) {
            self.targetName = "Bank";
            self.targetHealthScale = 0;
            self.targetHealthRatio = -1;
            return;
        }

        self.targetName = null;
        self.targetHealthScale = null;
        self.targetHealthRatio = null;
    }

    private static boolean isAttackable(NPC npc) {
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) return false;
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) return true;
        }
        return false;
    }

    private static List<String> activePrayerNames(Client client) {
        List<String> names = new ArrayList<>();
        for (Prayer prayer : Prayer.values()) {
            if (client.isPrayerActive(prayer)) {
                names.add(prayer.name());
            }
        }
        return names;
    }
}
