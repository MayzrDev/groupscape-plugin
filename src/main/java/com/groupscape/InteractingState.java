package com.groupscape;

import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;

public class InteractingState implements ConsumableState {
    private final transient String playerName;

    // Distinguishes an explicit "not interacting with anything" push from a real target, so the
    // server can tell "cleared" apart from "no update this batch" (which otherwise both look like
    // an absent field) and actually null out the target instead of replaying the last real one
    // forever. An empty name is what the server keys off of - see update_batcher.rs's
    // merge_group_member and its interacting COALESCE.
    private final transient boolean cleared;

    @Getter
    private final String name;
    @Getter
    private final int scale;
    @Getter
    private final int ratio;
    @Getter
    private final LocationState location;

    public InteractingState(String playerName, Actor actor, Client client) {
        this.playerName = playerName;
        this.cleared = false;

        // Non-combat NPCs (bankers, quest givers, Tool Leprechauns, etc.) can flash a
        // stale/default healthbar ratio from RuneLite for a tick even though they're not
        // fightable - combat level isn't reliable since some non-attackable NPCs still report
        // one. Whether "Attack" is actually a menu option, matching LocalRosterMemberFactory's
        // target-bar logic, can't fluctuate tick to tick.
        boolean combatCapable = !(actor instanceof NPC) || isAttackable((NPC) actor);
        this.scale = combatCapable ? actor.getHealthScale() : 0;
        this.ratio = combatCapable ? actor.getHealthRatio() : -1;
        this.name = actor.getName();

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, actor.getLocalLocation());
        this.location = new LocationState(playerName, worldPoint, false);
    }

    private InteractingState(String playerName) {
        this.playerName = playerName;
        this.cleared = true;
        this.name = "";
        this.scale = 0;
        this.ratio = -1;
        this.location = new LocationState(playerName, new WorldPoint(0, 0, 0), false);
    }

    /** An explicit "no target" push - see {@link #cleared}. */
    public static InteractingState notInteracting(String playerName) {
        return new InteractingState(playerName);
    }

    private static boolean isAttackable(NPC npc) {
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) return false;
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) return true;
        }
        return false;
    }

    @Override
    public Object get() {
        return this;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof InteractingState)) return false;

        // NOTE: For real interactions, we want to keep sending the data every tick until the
        // player stops, even if nothing changed about what's being interacted with - the UI
        // handles not showing the interaction once it goes stale. But once cleared, there's
        // nothing further to say - repeated clears are equal so they don't spam an update every
        // tick after the target actually goes away.
        return this.cleared && ((InteractingState) o).cleared;
    }
}
