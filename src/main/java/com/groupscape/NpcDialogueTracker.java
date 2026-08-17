package com.groupscape;

/**
 * {@code Actor.getInteracting()} only reports an NPC target during the walk-up/click moment -
 * once the dialogue box is actually open and the conversation is under way, it goes back to
 * null. Without this, both rich presence ("Talking to X") and dialogue interaction events would
 * drop the instant the chat box opened, which is the opposite of what they should show. Remembers
 * the last NPC actually interacted with and keeps reporting it for as long as the dialogue widget
 * stays open, clearing the moment it closes. Ported from groupscape-old. Must only be touched from
 * the client thread.
 */
public class NpcDialogueTracker {
    private Integer lastNpcId;
    private String lastNpcName;
    private int lastCombatLevel;

    /** Called whenever {@code getInteracting()} currently reports an NPC target. */
    public void observe(int npcId, String npcName, int combatLevel) {
        lastNpcId = npcId;
        lastNpcName = npcName;
        lastCombatLevel = combatLevel;
    }

    /** Called once the dialogue widget is no longer open and there's no live NPC target. */
    public void clear() {
        lastNpcId = null;
        lastNpcName = null;
        lastCombatLevel = 0;
    }

    public Integer lastNpcId() {
        return lastNpcId;
    }

    public String lastNpcName() {
        return lastNpcName;
    }

    public int lastCombatLevel() {
        return lastCombatLevel;
    }
}
