package com.groupscape;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Derives a short human-readable activity string ("Fighting Zulrah", "Talking
 * to Hans", "Browsing the bank") entirely from local Client state, so the
 * party overlay can show it without a server-side NPC/animation lookup table.
 */
public class RichPresenceState implements ConsumableState {
    private final String text;
    private final transient String playerName;

    RichPresenceState(String playerName, Client client, NpcDialogueTracker dialogueTracker) {
        this.playerName = playerName;
        this.text = computeText(client, dialogueTracker);
    }

    /**
     * Exposed so the party overlay can compute the same text for the local player's own row.
     * {@code dialogueTracker} may be null (falls back to the pre-tracker behavior of going idle
     * the instant a dialogue box swallows {@code getInteracting()}'s target).
     */
    public static String computeText(Client client, NpcDialogueTracker dialogueTracker) {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return null;
        }

        Actor interacting = player.getInteracting();
        if (interacting != null) {
            String name = interacting.getName();
            if (name == null) {
                return null;
            }

            int combatLevel = 0;
            if (interacting instanceof NPC) {
                combatLevel = ((NPC) interacting).getCombatLevel();
            } else if (interacting instanceof Player) {
                combatLevel = ((Player) interacting).getCombatLevel();
            }

            return (combatLevel > 0 ? "Fighting " : "Talking to ") + name;
        }

        // getInteracting() has already gone null once the dialogue box is actually open (see
        // NpcDialogueTracker) but the box is still up, so the player is still talking to
        // whoever they last targeted.
        if (dialogueTracker != null && dialogueTracker.lastNpcName() != null) {
            return "Talking to " + dialogueTracker.lastNpcName();
        }

        Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (bankItems != null && !bankItems.isHidden()) {
            return "Browsing the bank";
        }

        return null;
    }

    @Override
    public Object get() {
        return text;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RichPresenceState)) return false;

        RichPresenceState other = (RichPresenceState) o;
        return text == null ? other.text == null : text.equals(other.text);
    }
}
